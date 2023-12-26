package cn.wolfcode.mq.listener;

import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.web.controller.OrderInfoController;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = MQConstant.ORDER_PAY_TIMEOUT_CONSUMER_GROUP,
        topic = MQConstant.ORDER_PAY_TIMEOUT_TOPIC)
public class OrderTomeoutMessageListener implements RocketMQListener<OrderMessage> {
    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public void onMessage(OrderMessage message) {
        log.info("[订单超时未支付检查]准备删除本地标识:{}",message);
        orderInfoService.checkPayTimeout(message);
    }
}
