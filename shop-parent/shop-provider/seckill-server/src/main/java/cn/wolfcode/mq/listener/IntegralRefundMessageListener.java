package cn.wolfcode.mq.listener;

import cn.wolfcode.domain.RefundLog;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQTransactionListener(txProducerGroup = MQConstant.INTEGRAL_REFUND_TX_GROUP)
public class IntegralRefundMessageListener implements RocketMQLocalTransactionListener {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object o) {
        try {
            orderInfoService.integralRefundRollback((String)o);
            return RocketMQLocalTransactionState.UNKNOWN;
        } catch (Exception e) {
            e.printStackTrace();
            log.warn("[事务监听器]执行本地事务出现异常");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        try {
            //查询当前订单是否已经变成退款状态
            String orderNo = (String) message.getHeaders().get("orderNo");
            RefundLog refundLog=orderInfoService.selectRefundLogByOrderNo(orderNo);
            if (refundLog!=null){
                return RocketMQLocalTransactionState.COMMIT;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return RocketMQLocalTransactionState.ROLLBACK;
    }
}
