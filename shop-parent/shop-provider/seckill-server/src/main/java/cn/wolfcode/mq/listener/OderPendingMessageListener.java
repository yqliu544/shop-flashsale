package cn.wolfcode.mq.listener;

import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = MQConstant.ORDER_PEDDING_CONSUMER_GROUP,topic = MQConstant.ORDER_PEDDING_TOPIC)
public class OderPendingMessageListener implements RocketMQListener<OrderMessage> {
    @Autowired
    private IOrderInfoService orderInfoService;
    @Override
    public void onMessage(OrderMessage orderMessage) {
        orderInfoService.doSeckill(orderMessage.getSeckillId(),orderMessage.getUserPhone(),orderMessage.getTime());
    }
}
