package cn.wolfcode.mq;


public class MQConstant {
    //订单队列
    public static final String ORDER_PEDDING_TOPIC = "ORDER_PEDDING_TOPIC";
    //
    public static final String ORDER_PEDDING_CONSUMER_GROUP = "ORDER_PEDDING_CONSUMER_GROUP";
    //订单结果
    public static final String ORDER_RESULT_TOPIC = "ORDER_RESULT_TOPIC";
    //订单超时取消
    public static final String ORDER_PAY_TIMEOUT_TOPIC = "ORDER_PAY_TIMEOUT_TOPIC";
    public static final String ORDER_PAY_TIMEOUT_CONSUMER_GROUP = "ORDER_PAY_TIMEOUT_CONSUMER_GROUP";
    //取消本地标识
    public static final String CANCEL_SECKILL_OVER_SIGE_TOPIC = "CANCEL_SECKILL_OVER_SIGE_TOPIC";
    public static final String CANCEL_SECKILL_OVER_SIGE_CONSUMER_GROUP = "CANCEL_SECKILL_OVER_SIGE_CONSUMER_GROUP";
    //订单创建成功Tag
    public static final String ORDER_RESULT_SUCCESS_TAG = "SUCCESS";
    //订单创建成失败Tag
    public static final String ORDER_RESULT_FAIL_TAG = "FAIL";
    //延迟消息等级
    public static final int ORDER_PAY_TIMEOUT_DELAY_LEVEL = 13;
    //积分事务消息分组
    public static final String INTEGRAL_REFUND_TX_GROUP="integral-refund-tx-group";
    public static final String INTEGRAL_REFUND_TX_TOPIC="INTEGRAL_REFUND_TX_TOPIC";

}
