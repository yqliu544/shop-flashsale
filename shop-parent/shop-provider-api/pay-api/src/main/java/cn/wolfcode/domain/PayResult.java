package cn.wolfcode.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * @Description:
 * @author: 刘
 * @date: 2023年12月28日 下午 3:49
 */
@Getter
@Setter
public class PayResult {
    private String outTradeNo;
    private String tradeNo;
    private String totalAmount;

    public PayResult(String outTradeNo, String tradeNo, String totalAmount) {
        this.outTradeNo=outTradeNo;
        this.tradeNo=tradeNo;
        this.totalAmount=totalAmount;
    }
}
