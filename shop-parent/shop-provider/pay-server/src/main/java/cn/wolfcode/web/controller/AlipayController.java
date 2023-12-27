package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.config.AlipayProperties;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.web.msg.PayCodeMsg;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alipay")
public class AlipayController {
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private AlipayProperties alipayProperties;

    @PostMapping("/prepay")
    public Result<String> prepay(@RequestBody PayVo pay){
        //利用支付宝sdk api 向支付宝发起请求
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        //异步接收地址 仅支持http、https ，公网可访问
        request.setNotifyUrl(pay.getNotifyUrl());
        //同步跳转地址 仅支持http、https
        request.setReturnUrl(pay.getReturnUrl());
        //必传参数
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no",pay.getOutTradeNo());
        bizContent.put("total_amount",pay.getTotalAmount());
        bizContent.put("subject",pay.getSubject());
        bizContent.put("body",pay.getBody());
        bizContent.put("product_code","FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toString());
        try {
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            if (response.isSuccess()){
                log.info("[支付宝支付]发起支付成功，收到响应结果：{}",response.getBody());
                return Result.success(response.getBody());
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return Result.error(PayCodeMsg.PAY_FAILED);
    }
}
