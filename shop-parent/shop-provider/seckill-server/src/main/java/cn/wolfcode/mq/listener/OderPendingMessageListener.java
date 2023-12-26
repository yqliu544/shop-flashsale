package cn.wolfcode.mq.listener;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.mq.DefaultSendCallback;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMQResult;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = MQConstant.ORDER_PEDDING_CONSUMER_GROUP,topic = MQConstant.ORDER_PEDDING_TOPIC)
public class OderPendingMessageListener implements RocketMQListener<OrderMessage> {
    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Override
    public void onMessage(OrderMessage orderMessage) {
        log.info("[订单创建]{}", JSON.toJSONString(orderMessage));
        OrderMQResult result = new OrderMQResult();
        result.setToken(orderMessage.getToken());
        result.setTime(orderMessage.getTime());
        try {
            String orderNo = orderInfoService.doSeckill(orderMessage.getSeckillId(), orderMessage.getUserPhone(), orderMessage.getTime());
            result.setOrderNo(orderNo);
            result.setCode(Result.SUCCESS_CODE);
            result.setMsg("订单创建成功");
            //当下单成功后，发送一个延迟消息，检查订单支付状态，超过时间未支付，直接取消改订单
            orderMessage.setOrderNo(orderNo);
            Message<OrderMessage> orderMessageMessage = MessageBuilder.withPayload(orderMessage).build();
            rocketMQTemplate.asyncSend(MQConstant.ORDER_PAY_TIMEOUT_TOPIC,orderMessageMessage,new DefaultSendCallback("订单超时未支付"),2000,3);
        } catch (Exception e) {
            result.setCode(SeckillCodeMsg.SECKILL_ERROR.getCode());
            result.setMsg(SeckillCodeMsg.SECKILL_ERROR.getMsg());
            orderInfoService.failedRollback(orderMessage);
        }
        rocketMQTemplate.asyncSend(MQConstant.ORDER_RESULT_TOPIC,result,new DefaultSendCallback("订单结果"));

    }
}
