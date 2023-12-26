package cn.wolfcode.mq.listener;

import cn.wolfcode.core.WebsocketServer;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMQResult;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.io.IOException;

/**
 * @Description:
 * @author: 刘
 * @date: 2023年12月26日 下午 4:49
 */
@RocketMQMessageListener(consumerGroup = MQConstant.ORDER_RESULT_CONSUMER_GROUP, topic = MQConstant.ORDER_RESULT_TOPIC)
@Component
@Slf4j
public class OrderResultMessageListener implements RocketMQListener<OrderMQResult> {
    @Override
    public void onMessage(OrderMQResult orderMQResult) {
        String json = JSON.toJSONString(orderMQResult);
        log.info("[订单结果]收到订单结果消息:{}", json);
        try {
            int count=0;
            do{
                Session session = WebsocketServer.SESSION_MAP.get(orderMQResult.getToken());
                if (session != null) {
                    session.getBasicRemote().sendText(json);
                }
                count++;
                log.info("[订单结果]第{}次查询session对象获取失败，准备下一次获取。。。",count);
                Thread.sleep(500);
            }while (count<=5);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
