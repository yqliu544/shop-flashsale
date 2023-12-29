package cn.wolfcode.mapper;

import cn.wolfcode.domain.AccountLog;
import org.apache.ibatis.annotations.Param;


public interface AccountLogMapper {
    /**
     * 插入日志
     * @param accountLog
     */
    void insert(AccountLog accountLog);

    AccountLog selectByOutTradeNoAndType(@Param("outTradeNo") String outTradeNo, @Param("type") int type);
}
