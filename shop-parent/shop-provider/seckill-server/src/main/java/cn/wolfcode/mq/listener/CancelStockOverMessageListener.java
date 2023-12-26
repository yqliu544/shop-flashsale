package cn.wolfcode.mq.listener;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.mq.DefaultSendCallback;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMQResult;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.web.controller.OrderInfoController;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = MQConstant.CANCEL_SECKILL_OVER_SIGE_CONSUMER_GROUP,
        topic = MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC,messageModel = MessageModel.BROADCASTING)
public class CancelStockOverMessageListener implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("[取消本地标识]准备删除本地标识");
        Long seckillId = Long.parseLong(message);
        OrderInfoController.deleteKey(seckillId);
    }
}
