package cn.wolfcode.service;


import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayResult;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.OrderMessage;

import java.util.Map;

/**
 * Created by wolfcode
 */
public interface IOrderInfoService {

    OrderInfo selectByUserIdAndSeckillId(Long phone, Long seckillId, Integer time);

    String doSeckill(SeckillProductVo seckillProductVo, Long phone);
    String doSeckill(Long seckillId, Long phone,Integer time);

    OrderInfo selectByOrderNo(String orderNo);

    void failedRollback(OrderMessage orderMessage);

    void checkPayTimeout(OrderMessage message);

    String onlinePay(String orderNo);

    void alipaySuccess(PayResult payResult);

    void alipayfund(String orderNo);
}
