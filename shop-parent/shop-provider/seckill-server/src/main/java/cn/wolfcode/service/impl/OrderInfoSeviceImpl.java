package cn.wolfcode.service.impl;

import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.feign.IntegralFeignApi;
import cn.wolfcode.feign.PaymentFeignApi;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.mq.DefaultSendCallback;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.AssertUtils;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by wolfcode
 */
@Slf4j
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private PaymentFeignApi paymentFeignApi;
    @Autowired
    private IntegralFeignApi integralFeignApi;

    @Override
    public OrderInfo selectByUserIdAndSeckillId(Long userId, Long seckillId, Integer time) {
        return orderInfoMapper.selectByUserIdAndSeckillId(userId, seckillId, time);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doSeckill(SeckillProductVo sp, Long phone) {
        //扣除库存
        seckillProductService.decrStockCount(sp.getId(), sp.getTime());
        //创建订单信息对象
        OrderInfo orderInfo = buildOrderInfo(phone, sp);
        //保存订单
        orderInfoMapper.insert(orderInfo);

        return orderInfo.getOrderNo();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doSeckill(Long seckillId, Long phone, Integer time) {
        SeckillProductVo seckillProductVo = seckillProductService.selectByIdAndTime(seckillId, time);
        return this.doSeckill(seckillProductVo, phone);
    }

    @Override
    public OrderInfo selectByOrderNo(String orderNo) {
        return orderInfoMapper.selectById(orderNo);
    }

    @Override
    public void failedRollback(OrderMessage orderMessage) {
        //回补redis

        this.rollbackRedisStock(orderMessage.getSeckillId(), orderMessage.getTime());
        //删除用户标识
        String userOrderFalg = SeckillRedisKey.SECKILL_ORDER_HASH.join(orderMessage.getSeckillId() + "");
        redisTemplate.opsForHash().delete(userOrderFalg, orderMessage.getUserPhone() + "");
        //删除本地标识
        rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC, orderMessage.getSeckillId(), new DefaultSendCallback("下单失败回滚"));
    }

    private void rollbackRedisStock(Long seckillId, Integer time) {
        Long seckillCount = seckillProductService.selectStockCountById(seckillId);
        String key = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(time + "");
        redisTemplate.opsForHash().put(key, seckillId + "", seckillCount + "");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkPayTimeout(OrderMessage message) {
        int row = orderInfoMapper.changePayStatus(message.getOrderNo(), OrderInfo.STATUS_CANCEL, OrderInfo.PAY_TYPE_ONLINE);
        if (row > 0) {
            //mysql秒杀商品库存数量+1
            seckillProductService.incrStockCount(message.getSeckillId());
            //订单信息回滚（redis库存回滚，删除用户下单标识，删除本地标识）
            this.failedRollback(message);
        }
    }

    @Override
    public String onlinePay(String orderNo) {
        //查询订单对象
        OrderInfo orderInfo = this.selectByOrderNo(orderNo);
        //判断订单状态
        if (orderInfo.getStatus() != OrderInfo.STATUS_ARREARAGE) {
            return "订单异常，无法发起支付";
        }
        PayVo payVo = new PayVo();
        payVo.setBody("秒杀：" + orderInfo.getProductName());
        payVo.setSubject(orderInfo.getProductName());
        payVo.setOutTradeNo(orderNo);
        payVo.setTotalAmount(orderInfo.getSeckillPrice().toString());
        //远程调用支付服务
        Result<String> result = paymentFeignApi.prepay(payVo);
        return result.checkAndGet();
    }

    @Override
    public void alipaySuccess(PayResult payResult) {
        OrderInfo orderInfo = this.selectByOrderNo(payResult.getOutTradeNo());
        AssertUtils.notNull(orderInfo, "订单信息有误");
        AssertUtils.isTrue(orderInfo.getSeckillPrice().toString().equals(payResult.getTotalAmount()), "支付金额有误");
        int row = orderInfoMapper.changePayStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_ONLINE);
        AssertUtils.isTrue(row > 0, "订单状态修改失败");
        PayLog payLog = new PayLog();
        payLog.setPayType(PayLog.PAY_TYPE_ONLINE);
        payLog.setTotalAmount(payResult.getTotalAmount());
        payLog.setOutTradeNo(payResult.getOutTradeNo());
        payLog.setTradeNo(payResult.getTradeNo());
        payLog.setNotifyTime(System.currentTimeMillis() + "");
        payLogMapper.insert(payLog);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void refund(String orderNo) {
        OrderInfo orderInfo = this.selectByOrderNo(orderNo);
        //判断订单是否为已支付
        AssertUtils.isTrue(OrderInfo.STATUS_ACCOUNT_PAID.equals(orderInfo.getStatus()), "订单状态错误");
        Result<Boolean> result = null;
        RefundVo refundVo = new RefundVo(orderNo, orderInfo.getSeckillPrice(), "不想要了");
        if (orderInfo.getPayType() == OrderInfo.PAY_TYPE_ONLINE) {
            //支付退款
            result = paymentFeignApi.refund(refundVo);
        } else {
            //积分退款
            //result=integralFeignApi.refund(refundVo);
            //发送rocketmq事务消息
            Message<RefundVo> message = MessageBuilder.withPayload(refundVo).setHeader("orderNo",orderNo).build();
            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(MQConstant.INTEGRAL_REFUND_TX_GROUP, MQConstant.INTEGRAL_REFUND_TX_TOPIC, message, orderNo);
            //判断sendResult是否为commit 判断本地事务是否执行成功
            if (sendResult.getLocalTransactionState().equals(LocalTransactionState.COMMIT_MESSAGE)) {
                log.info("[积分退款]积分退款本地事务执行成功，等待远程服务执行完成");
                return;
            }
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        if (result == null || result.hasError() || !result.getData()) {
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        refundRollback(orderInfo);
    }

    private void refundRollback(OrderInfo orderInfo) {
        //更新订单状态已退款
        int row = orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
        AssertUtils.isTrue(row > 0, "退款失败，更新状态异常");
        //库存回补
        seckillProductService.incrStockCount(orderInfo.getSeckillId());
        this.rollbackRedisStock(orderInfo.getSeckillId(), orderInfo.getSeckillTime());
        rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC, orderInfo.getSeckillId(), new DefaultSendCallback("取消本地标识"));
        RefundLog refundLog = new RefundLog();
        refundLog.setRefundReason("用户申请退款：" + orderInfo.getProductName());
        refundLog.setRefundTime(new Date());
        refundLog.setRefundType(orderInfo.getPayType());
        refundLog.setRefundAmount(orderInfo.getSeckillPrice().toString());
        refundLog.setOutTradeNo(orderInfo.getOrderNo());
        refundLogMapper.insert(refundLog);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void integralPay(String orderNo, Long phone) {
        OrderInfo orderInfo = this.selectByOrderNo(orderNo);
        AssertUtils.isTrue(OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus()), "订单状态错误");
        //判断当前用户是否为这个订单的用户
        OperateIntergralVo intergralVo = new OperateIntergralVo();
        intergralVo.setInfo("积分秒杀：" + orderInfo.getProductName());
        intergralVo.setValue(orderInfo.getIntergral());
        intergralVo.setUserId(phone);
        intergralVo.setOutTradeNo(orderNo);
        //远程调用支付服务发起支付
        Result<String> result = integralFeignApi.prepay(intergralVo);
        String tradeNo = result.checkAndGet();
        //更新订单状态
        int row = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_INTERGRAL);
        AssertUtils.isTrue(row > 0, "订单状态修改失败");
        PayLog payLog = new PayLog();
        payLog.setPayType(PayLog.PAY_TYPE_INTERGRAL);
        payLog.setTotalAmount(intergralVo.getValue() + "");
        payLog.setOutTradeNo(orderNo);
        payLog.setTradeNo(tradeNo);
        payLog.setNotifyTime(System.currentTimeMillis() + "");
        payLogMapper.insert(payLog);


    }

    @Override
    public void integralRefundRollback(String o) {
        OrderInfo orderInfo = orderInfoMapper.selectById(o);
        this.refundRollback(orderInfo);
    }

    @Override
    public RefundLog selectRefundLogByOrderNo(String orderNo) {

        return refundLogMapper.selectByOrderNo(orderNo);
    }


    private OrderInfo buildOrderInfo(Long userId, SeckillProductVo vo) {
        Date now = new Date();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCreateDate(now);
        orderInfo.setDeliveryAddrId(1L);
        orderInfo.setIntergral(vo.getIntergral());
        orderInfo.setOrderNo(IdGenerateUtil.get().nextId() + "");
        orderInfo.setPayType(OrderInfo.PAY_TYPE_ONLINE);
        orderInfo.setProductCount(1);
        orderInfo.setProductId(vo.getProductId());
        orderInfo.setProductImg(vo.getProductImg());
        orderInfo.setProductName(vo.getProductName());
        orderInfo.setProductPrice(vo.getProductPrice());
        orderInfo.setSeckillDate(now);
        orderInfo.setSeckillId(vo.getId());
        orderInfo.setSeckillPrice(vo.getSeckillPrice());
        orderInfo.setSeckillTime(vo.getTime());
        orderInfo.setStatus(OrderInfo.STATUS_ARREARAGE);
        orderInfo.setUserId(userId);
        return orderInfo;
    }
}
