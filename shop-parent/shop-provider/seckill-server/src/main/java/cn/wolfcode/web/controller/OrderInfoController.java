package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.redis.CommonRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.DateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    //    @Autowired
//    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    @RequireLogin
    @RequestMapping("/doSeckill")
    public Result<String> doSeckill(Integer time, Long seckillId, @RequestHeader(CommonConstants.TOKEN_NAME) String token) {
        // 1. 基于 token 获取到用户信息(必须登录)
        UserInfo userInfo = this.getUserByToken(token);
        // 2. 基于场次+秒杀id获取到秒杀商品对象
        SeckillProductVo vo = seckillProductService.selectByIdAndTime(seckillId, time);
        if (vo == null) {
            throw new BusinessException(SeckillCodeMsg.REMOTE_DATA_ERROR);
        }
        // 3. 判断时间是否大于开始时间 && 小于 开始时间+2小时
        if (!DateUtil.isLegalTime(vo.getStartDate(), time)) {
            throw new BusinessException(SeckillCodeMsg.OUT_OF_SECKILL_TIME_ERROR);
        }
        // 4. 判断用户是否重复下单
        // 基于用户 + 秒杀 id + 场次查询订单, 如果存在订单, 说明用户已经下过单
        OrderInfo orderInfo = orderInfoService.selectByUserIdAndSeckillId(userInfo.getPhone(), seckillId, time);
        if (orderInfo != null) {
            throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
        }
        // 5. 判断库存是否充足
        if (vo.getStockCount() <= 0) {
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        // 6. 执行下单操作(减少库存, 创建订单)
        String orderNo = orderInfoService.doSeckill(userInfo, vo);
        return Result.success(orderNo);
    }

    private UserInfo getUserByToken(String token) {
        return JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)), UserInfo.class);
    }
}
