package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.service.IUsableIntegralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/intergral")
public class IntegralController {
    @Autowired
    private IUsableIntegralService usableIntegralService;

    @PostMapping("/prepay")
    public Result<String> prepay(@RequestBody OperateIntergralVo vo){
        String tradeNo=usableIntegralService.doPay(vo);
        return Result.success(tradeNo);
    }
    @PostMapping("/integral/refund")
    Result<Boolean> refund(@RequestBody RefundVo refundVo){
        boolean ret=usableIntegralService.doRefund(refundVo);
        return Result.success(ret);
    };
}
