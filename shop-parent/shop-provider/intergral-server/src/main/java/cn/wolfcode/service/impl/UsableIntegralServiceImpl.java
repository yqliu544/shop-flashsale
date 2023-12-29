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
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;


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
        usableIntegralMapper.addIntergral(decrLog.getUserId(), Long.valueOf(refundVo.getRefundAmount()));
        AccountLog log = new AccountLog();
        log.setAmount( Long.valueOf(refundVo.getRefundAmount()));
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
