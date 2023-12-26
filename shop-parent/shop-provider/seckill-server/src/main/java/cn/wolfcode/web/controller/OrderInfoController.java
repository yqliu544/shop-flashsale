package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.common.web.resolver.RequestUser;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.DefaultSendCallback;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.CommonRedisKey;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.AssertUtils;
import cn.wolfcode.util.DateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {

    private static final Map<Long, Boolean> STOCK_OVER_FLOW_MAP = new ConcurrentHashMap<>();
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    public static void deleteKey(Long key){
        STOCK_OVER_FLOW_MAP.remove(key);
    }

    @RequireLogin
    @PostMapping("/doSeckill")
    public Result<?> doSeckill(Long seckillId, Integer time, @RequestUser UserInfo userInfo,@RequestHeader("token") String token) {
        //判断库存是否已经卖完
        if (STOCK_OVER_FLOW_MAP.get(seckillId) != null && STOCK_OVER_FLOW_MAP.get(seckillId)) {
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        //基于秒杀id+场次查询秒杀商品对象
        SeckillProductVo seckillProductVo = seckillProductService.selectByIdAndTime(seckillId, time);
        //        AssertUtils.notNull(seckillProductVo, "非法操作");
        if (seckillProductVo == null) {
            return Result.error(SeckillCodeMsg.REMOTE_DATA_ERROR);
        }
        //判断当前时间是否在秒杀时间范围内
        boolean range = betweenSeckillTime(seckillProductVo);
        //        AssertUtils.isTrue(range, "不在秒杀时间范围内");
        if (!range) {
            return Result.error(SeckillCodeMsg.OUT_OF_SECKILL_TIME_ERROR);
        }
        //判断用户是否已经下过单
        String userOrderFlag = SeckillRedisKey.SECKILL_ORDER_HASH.join(seckillId + "");
        Boolean aBoolean = redisTemplate.opsForHash().putIfAbsent(userOrderFlag, userInfo.getPhone() + "", "1");
        //        OrderInfo orderInfo = orderInfoService.selectByUserIdAndSeckillId(userInfo.getPhone(), seckillId, time);
        //        AssertUtils.isTrue(aBoolean, "不能重复下单");
        if (!aBoolean) {
            return Result.error(SeckillCodeMsg.REPEAT_SECKILL);
        }
        try {
            //判断库存是否充足
            String key = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(time + "");
            Long remain = redisTemplate.opsForHash().increment(key, seckillId + "", -1);
            AssertUtils.isTrue(remain >= 0, "商品已经卖完了");

            //创建订单，扣除库存，返回订单id
//            String orderId = orderInfoService.doSeckill(seckillProductVo, userInfo.getPhone());
            rocketMQTemplate.asyncSend(MQConstant.ORDER_PEDDING_TOPIC,new OrderMessage(time,seckillId,token,userInfo.getPhone(),null),new DefaultSendCallback("创建订单"));
            return Result.success("订单创建中。。。。。。");
        } catch (BusinessException e) {
            STOCK_OVER_FLOW_MAP.put(seckillId, true);
            redisTemplate.opsForHash().delete(userOrderFlag, userInfo.getPhone() + "");
            return Result.error(e.getCodeMsg());
        }

    }

    private boolean betweenSeckillTime(SeckillProductVo seckillProductVo) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(seckillProductVo.getStartDate());
        instance.set(Calendar.HOUR_OF_DAY, seckillProductVo.getTime());
        Date startDate = instance.getTime();
        instance.add(Calendar.HOUR_OF_DAY, 2);
        Date endDate = instance.getTime();
        long now = System.currentTimeMillis();
        return startDate.getTime() <= now && now < endDate.getTime();
    }


    private UserInfo getUserByToken(String token) {
        return JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)), UserInfo.class);
    }
    @RequireLogin
    @GetMapping("/find")
    public Result<OrderInfo> findById(String orderNo,@RequestUser UserInfo userInfo){
        OrderInfo orderInfo = orderInfoService.selectByOrderNo(orderNo);
        Long userId = orderInfo.getUserId();
        if (!userInfo.getPhone().equals(userId)) {
            return Result.error(SeckillCodeMsg.REMOTE_DATA_ERROR);
        }
        return Result.success(orderInfo);
    }
}
