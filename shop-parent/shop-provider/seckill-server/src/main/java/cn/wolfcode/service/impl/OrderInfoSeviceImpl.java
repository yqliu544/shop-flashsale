package cn.wolfcode.service.impl;

import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.SeckillProductVo;
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
import cn.wolfcode.util.IdGenerateUtil;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by wolfcode
 */
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

    @Override
    public OrderInfo selectByUserIdAndSeckillId(Long userId, Long seckillId, Integer time) {
        return orderInfoMapper.selectByUserIdAndSeckillId(userId, seckillId, time);
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doSeckill(SeckillProductVo sp, Long phone) {
        //扣除库存
        seckillProductService.decrStockCount(sp.getId(),sp.getTime());
        //创建订单信息对象
        OrderInfo orderInfo = buildOrderInfo(phone, sp);
        //保存订单
        orderInfoMapper.insert(orderInfo);

        return orderInfo.getOrderNo();
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doSeckill(Long seckillId, Long phone,Integer time) {
        SeckillProductVo seckillProductVo = seckillProductService.selectByIdAndTime(seckillId, time);
        return this.doSeckill(seckillProductVo,phone);
    }

    @Override
    public OrderInfo selectByOrderNo(String orderNo) {
        return orderInfoMapper.selectById(orderNo);
    }

    @Override
    public void failedRollback(OrderMessage orderMessage) {
        //回补redis
        Long seckillCount=seckillProductService.selectStockCountById(orderMessage.getSeckillId());
        String key = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(orderMessage.getTime() + "");
        redisTemplate.opsForHash().put(key,orderMessage.getSeckillId()+"",seckillCount+"");
        //删除用户标识
        String userOrderFalg = SeckillRedisKey.SECKILL_ORDER_HASH.join(orderMessage.getSeckillId() + "");
        redisTemplate.opsForHash().delete(userOrderFalg,orderMessage.getUserPhone()+"");
        //删除本地标识
        rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC,orderMessage.getSeckillId(),new DefaultSendCallback("下单失败回滚"));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkPayTimeout(OrderMessage message) {
        int row = orderInfoMapper.changePayStatus(message.getOrderNo(), OrderInfo.STATUS_CANCEL, OrderInfo.PAY_TYPE_ONLINE);
        if (row>0){
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
        if (orderInfo.getStatus()!=OrderInfo.STATUS_ARREARAGE){
            return "订单异常，无法发起支付";
        }
        PayVo payVo = new PayVo();
        payVo.setBody("秒杀："+orderInfo.getProductName());
        payVo.setSubject(orderInfo.getProductName());
        payVo.setOutTradeNo(orderNo);
        payVo.setTotalAmount(orderInfo.getSeckillPrice().toString());
        //远程调用支付服务
        Result<String> result = paymentFeignApi.prepay(payVo);
        return result.checkAndGet();
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
