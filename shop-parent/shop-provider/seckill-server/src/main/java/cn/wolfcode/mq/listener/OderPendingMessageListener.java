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
        } catch (Exception e) {
            result.setCode(SeckillCodeMsg.SECKILL_ERROR.getCode());
            result.setMsg(SeckillCodeMsg.SECKILL_ERROR.getMsg());
            e.printStackTrace();
        }
        rocketMQTemplate.asyncSend(MQConstant.ORDER_RESULT_TOPIC,result,new DefaultSendCallback("订单结果"));

    }
}
