package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("intergral-service")
public interface IntegralFeignApi {

    @PostMapping("/integral/prepay")
    public Result<String> prepay(@RequestBody OperateIntergralVo vo);
    @PostMapping("/integral/refund")
    Result<Boolean> refund(@RequestBody RefundVo refundVo);
}
