package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.common.web.resolver.RequestUser;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.redis.CommonRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.AssertUtils;
import cn.wolfcode.util.DateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Calendar;
import java.util.Date;


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
    @PostMapping("/doSeckill")
    public Result<?> doSeckill(Long seckillId,Integer time,@RequestUser UserInfo userInfo){
        //基于秒杀id+场次查询秒杀商品对象
        SeckillProductVo seckillProductVo = seckillProductService.selectByIdAndTime(seckillId, time);
        AssertUtils.notNull(seckillProductVo,"非法操作");
        //判断当前时间是否在秒杀时间范围内
        boolean range=betweenSeckillTime(seckillProductVo);
        AssertUtils.isTrue(range,"不在秒杀时间范围内");
        //判断库存是否充足
        AssertUtils.isTrue(seckillProductVo.getStockCount()>0,"商品已经卖完了");
        //判断用户是否已经下过单
        OrderInfo orderInfo = orderInfoService.selectByUserIdAndSeckillId(userInfo.getPhone(), seckillId, time);
        AssertUtils.isTrue(orderInfo==null,"不能重复下单");
        //创建订单，扣除库存，返回订单id
        String orderId=orderInfoService.doSeckill(seckillProductVo,userInfo.getPhone());
        return Result.success();
    }

    private boolean betweenSeckillTime(SeckillProductVo seckillProductVo) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(seckillProductVo.getStartDate());
        instance.set(Calendar.HOUR_OF_DAY,seckillProductVo.getTime());
        Date startDate = instance.getTime();
        instance.add(Calendar.HOUR_OF_DAY,2);
        Date endDate = instance.getTime();
        long now = System.currentTimeMillis();
        return startDate.getTime()<=now && now<endDate.getTime();
    }


    private UserInfo getUserByToken(String token) {
        return JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)), UserInfo.class);
    }
}
