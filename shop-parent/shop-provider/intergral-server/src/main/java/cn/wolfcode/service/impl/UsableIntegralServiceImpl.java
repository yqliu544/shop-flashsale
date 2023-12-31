package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.AccountLog;
import cn.wolfcode.domain.AccountTransaction;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.mapper.AccountLogMapper;
import cn.wolfcode.mapper.AccountTransactionMapper;
import cn.wolfcode.mapper.UsableIntegralMapper;
import cn.wolfcode.service.IUsableIntegralService;
import cn.wolfcode.util.AssertUtils;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.IntergralCodeMsg;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;


@Service
public class UsableIntegralServiceImpl implements IUsableIntegralService {
    @Autowired
    private UsableIntegralMapper usableIntegralMapper;
    @Autowired
    private AccountTransactionMapper accountTransactionMapper;
    @Autowired
    private AccountLogMapper accountLogMapper;
    @Autowired
    private IdGenerateUtil idGenerateUtil;

    @Override
    public String tryPayment(OperateIntergralVo vo, BusinessActionContext ctx) {
        //实现防悬挂，直接向sql插入事务控制记录
        String tradeNo=idGenerateUtil.nextId()+"";
        insertTxlog(vo, ctx,tradeNo,AccountLog.TYPE_DECR,AccountTransaction.STATE_TRY);
        //冻结积分，判断是否冻结成功
        int row = usableIntegralMapper.freezeIntergral(vo.getUserId(), vo.getValue());
        AssertUtils.isTrue(row>0,"账户余额不足");
        Map<String, Object> actionContext = ctx.getActionContext();
        actionContext.put("tradeNo",tradeNo);
        return tradeNo;
    }

    private void insertTxlog(OperateIntergralVo vo, BusinessActionContext ctx,String tradeNo,Integer type,Integer state) {
        AccountTransaction tx = new AccountTransaction();
        tx.setAmount(vo.getValue());
        tx.setType(type);
        tx.setGmtCreated(new Date());
        tx.setGmtModified(new Date());
        tx.setUserId(vo.getUserId());
        tx.setTradeNo(tradeNo);
        tx.setState(state);
        tx.setTxId(ctx.getXid());
        tx.setActionId(ctx.getBranchId());
        accountTransactionMapper.insert(tx);
    }

    @Override
    public void commitPayment(BusinessActionContext bu) {
        Object obj = bu.getActionContext("vo");
        JSONObject vo= (JSONObject) obj;
        AccountTransaction tx = accountTransactionMapper.get(bu.getXid(), bu.getBranchId());
        if (tx==null){
            return ;
        }
        if (tx.getState()==AccountTransaction.STATE_COMMIT){
            return ;
        }else if (AccountTransaction.STATE_CANCEL== tx.getState()){
            return ;
        }
        int row = usableIntegralMapper.commitChange(vo.getLong("userId"), vo.getLong("value"));
        AccountLog log = new AccountLog();
        log.setAmount(vo.getLong("value"));
        log.setInfo(vo.getString("info"));
        log.setGmtTime(new Date());
        String tradeNo = (String) bu.getActionContext("tradeNo");
        log.setTradeNo(tx.getTradeNo());
        log.setType(AccountLog.TYPE_INCR);
        log.setUserId(vo.getLong("userId"));
        log.setOutTradeNo(vo.getString("outTradeNo"));
        accountLogMapper.insert(log);
        int row2 = accountTransactionMapper.updateAccountTransactionState(bu.getXid(), bu.getBranchId(), AccountTransaction.STATE_COMMIT, AccountTransaction.STATE_TRY);

        return ;
    }

    @Override
    public void rollbackPayment(BusinessActionContext bu) {
        Object obj = bu.getActionContext("vo");
        JSONObject vo= (JSONObject) obj;
        AccountTransaction tx = accountTransactionMapper.get(bu.getXid(), bu.getBranchId());
        if (tx==null){
            try {
                this.insertTxlog(vo.toJavaObject(OperateIntergralVo.class),bu,tx.getTradeNo(),AccountLog.TYPE_DECR,AccountTransaction.STATE_CANCEL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        if (tx.getState()==AccountTransaction.STATE_CANCEL){
            return;
        }else if (tx.getState()==AccountTransaction.STATE_COMMIT){
            return;
        }
        usableIntegralMapper.unFreezeIntergral(vo.getLong("userId"),vo.getLong("value"));
        accountTransactionMapper.updateAccountTransactionState(bu.getXid(), bu.getBranchId(), AccountTransaction.STATE_CANCEL, AccountTransaction.STATE_TRY);

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doPay(OperateIntergralVo vo) {
        int row = usableIntegralMapper.decrIntegral(vo.getUserId(), vo.getValue());
        AssertUtils.isTrue(row > 0, "账户积分不足");
        AccountLog log = new AccountLog();
        log.setAmount(vo.getValue());
        log.setInfo(vo.getInfo());
        log.setGmtTime(new Date());
        log.setTradeNo(idGenerateUtil.nextId() + "");
        log.setOutTradeNo(vo.getOutTradeNo());
        log.setType(AccountLog.TYPE_DECR);
        log.setUserId(vo.getUserId());
        accountLogMapper.insert(log);
        return log.getTradeNo();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean doRefund(RefundVo refundVo) {
        AccountLog decrLog = accountLogMapper.selectByOutTradeNoAndType(refundVo.getOutTradeNo(), AccountLog.TYPE_DECR);
        AssertUtils.notNull(decrLog, "退款失败");
        AssertUtils.isTrue(new BigDecimal(refundVo.getRefundAmount()).compareTo(new BigDecimal(decrLog.getAmount())) <= 0, "退款金额不能大于支付金额");
        usableIntegralMapper.addIntergral(decrLog.getUserId(), new BigDecimal(refundVo.getRefundAmount()).longValue());
        AccountLog log = new AccountLog();
        log.setAmount(new BigDecimal(refundVo.getRefundAmount()).longValue());
        log.setInfo(refundVo.getRefundReason());
        log.setGmtTime(new Date());
        log.setTradeNo(idGenerateUtil.nextId() + "");
        log.setType(AccountLog.TYPE_INCR);
        log.setUserId(decrLog.getUserId());
        log.setOutTradeNo(refundVo.getOutTradeNo());
        accountLogMapper.insert(log);
        return true;
    }
}
