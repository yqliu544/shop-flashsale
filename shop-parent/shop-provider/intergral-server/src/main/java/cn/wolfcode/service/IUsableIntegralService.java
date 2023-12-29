package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.RefundVo;


public interface IUsableIntegralService {
    String doPay(OperateIntergralVo vo);

    boolean doRefund(RefundVo refundVo);
}
