package cn.wolfcode.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;


@Setter@Getter
public class RefundVo implements Serializable {
    private String outTradeNo;//交易订单号
    private String refundAmount;//退款金额
    private String refundReason;//退款原因

    public RefundVo(String orderNo, BigDecimal seckillPrice, String reason) {
        this.outTradeNo=orderNo;
        this.refundAmount=seckillPrice.toPlainString();
        this.refundReason=reason;
    }
}
