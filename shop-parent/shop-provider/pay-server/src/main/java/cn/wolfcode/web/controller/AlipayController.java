package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.config.AlipayProperties;
import cn.wolfcode.domain.PayResult;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.feign.SeckillFeignService;
import cn.wolfcode.util.AssertUtils;
import cn.wolfcode.web.msg.PayCodeMsg;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alipay")
public class AlipayController {
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private AlipayProperties alipayProperties;
    @Autowired
    private SeckillFeignService seckillFeignService;

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

    @PostMapping("/notify_url")
    public String notifyUrl(HttpServletRequest request){
        //接收参数
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParameters = request.getParameterMap();
        for(Iterator iter=requestParameters.keySet().iterator();iter.hasNext();){
            String name = (String) iter.next();
            String[] values = requestParameters.get(name);
            String valueStr="";
            for (int i = 0; i < values.length; i++) {
                valueStr=(i==values.length-1)?valueStr+values[i]:valueStr+values[i]+"";
            }
            params.put(name,valueStr);
        }
        try {
            boolean verify_result = AlipaySignature.rsaCheckV1(params, alipayProperties.getAlipayPublicKey(), alipayProperties.getCharset(), "RSA2");
            if (verify_result){
                String outTradeNo=request.getParameter("out_trade_no");//订单号
                String tradeNo=request.getParameter("trade_no");//支付宝交易号
                String tradeStatus=request.getParameter("trade_status");//交易状态
                String totalAmount=request.getParameter("total_amount");//支付金额
                if (tradeStatus.equals("TRADE_FINISHED")){
                    log.info("[支付宝异步回调]收到交易！");
                }else if (tradeStatus.equals("TRADE_SUCCESS")){
                    log.info("[支付宝异步回调]订单已支付成功：{}",outTradeNo);
                    PayResult payResult = new PayResult(outTradeNo, tradeNo, totalAmount);
                    Result<?> ret = seckillFeignService.updateOrderPaySuccess(payResult);
                    AssertUtils.isError(ret.hasError(),"更新订单");
                }
                return "success";
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();

        }
        return "fail";
    }

    @GetMapping("/return_url")
    public void returnUrl(HttpServletRequest request, HttpServletResponse response){
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParameters = request.getParameterMap();
        for(Iterator iter=requestParameters.keySet().iterator();iter.hasNext();){
            String name = (String) iter.next();
            String[] values = requestParameters.get(name);
            String valueStr="";
            for (int i = 0; i < values.length; i++) {
                valueStr=(i==values.length-1)?valueStr+values[i]:valueStr+values[i]+"";
            }
            params.put(name,valueStr);
        }
        try {
            boolean verify_result = AlipaySignature.rsaCheckV1(params, alipayProperties.getAlipayPublicKey(), alipayProperties.getCharset(), "RSA2");
            AssertUtils.isTrue(verify_result,"支付宝回调签名验证失败");
            String out_trade_no = request.getParameter("out_trade_no");
            response.sendRedirect("redirect:http://localhost/order_detail.html?orderNo="+out_trade_no);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @PostMapping("/refund")
    public Result<Boolean> refund(@RequestBody RefundVo refundVo){
        AlipayTradeRefundRequest alipay_request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(refundVo.getOutTradeNo());
        model.setRefundAmount(refundVo.getRefundAmount());
        model.setRefundReason(refundVo.getRefundReason());
        alipay_request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = alipayClient.execute(alipay_request);
            AssertUtils.isTrue(response.isSuccess(),response.getSubMsg());
            //判断是否支付成功
            if ("Y".equalsIgnoreCase(response.getFundChange())){
                return Result.success(true);
            }
            //如果fund——change=N 应该再调用查询接口判断是否支付成功
            AlipayTradeFastpayRefundQueryRequest refundQueryRequest = new AlipayTradeFastpayRefundQueryRequest();
            AlipayTradeFastpayRefundQueryModel refundQueryModel = new AlipayTradeFastpayRefundQueryModel();
            refundQueryModel.setOutTradeNo(refundVo.getOutTradeNo());
            refundQueryModel.setOutRequestNo(refundVo.getOutTradeNo());
            refundQueryRequest.setBizModel(refundQueryModel);
            AlipayTradeFastpayRefundQueryResponse refundQueryResponse = alipayClient.execute(refundQueryRequest);
            if ("10000".equals(refundQueryResponse.getCode())&& "REFUND_SUCCESS".equalsIgnoreCase(refundQueryResponse.getRefundStatus())){
                return Result.success(true);
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
            return Result.error(new CodeMsg(506001,e.getMessage()));
        }
        return Result.success(false);
    }

}
