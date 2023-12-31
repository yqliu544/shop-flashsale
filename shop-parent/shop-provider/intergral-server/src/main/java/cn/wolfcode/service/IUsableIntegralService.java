package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.RefundVo;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface IUsableIntegralService {

    @TwoPhaseBusinessAction(name = "tryPayment",commitMethod = "commitPayment",rollbackMethod = "rollbackPayment")
    String tryPayment(@BusinessActionContextParameter(paramName = "vo") OperateIntergralVo vo, BusinessActionContext businessActionContext);
    void commitPayment(BusinessActionContext bu);
    void rollbackPayment(BusinessActionContext bu);

    String doPay(OperateIntergralVo vo);

    boolean doRefund(RefundVo refundVo);
}
