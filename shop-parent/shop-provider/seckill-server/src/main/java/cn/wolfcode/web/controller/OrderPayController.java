package cn.wolfcode.web.controller;


import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayResult;
import cn.wolfcode.service.IOrderInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.Map;


@RestController
@RequestMapping("/orderPay")
@RefreshScope
public class OrderPayController {
    @Autowired
    private IOrderInfoService orderInfoService;
    @GetMapping("/pay")
    public Result<String> dopay(String orderNo,Integer type){
        if (type== OrderInfo.PAY_TYPE_ONLINE){
            return Result.success(orderInfoService.onlinePay(orderNo));
        }else {
            return null;
        }

    }

    @PostMapping("/success")
    public Result<?> alipaySuccess(@RequestBody PayResult payResult){
        orderInfoService.alipaySuccess(payResult);
        return Result.success();
    }

    @GetMapping("/refund")
    public Result<String> refund(String orderNo){
        orderInfoService.refund(orderNo);

        return Result.success();
    }
}
