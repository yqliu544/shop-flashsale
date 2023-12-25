package cn.wolfcode.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
@Slf4j
public class DefaultSendCallback implements SendCallback {

    private String mag;

    public DefaultSendCallback(String mag) {
        this.mag = mag;
    }

    @Override
    public void onSuccess(SendResult sendResult) {
        log.info("[{}]消息发送成功，消息id={}",mag,sendResult.getMsgId());
    }

    @Override
    public void onException(Throwable throwable) {
        log.warn("[{}]消息发送成功，消息id={}",mag,throwable.getMessage());
    }
}
