package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("pay-service")
public interface PaymentFeignApi {

    @PostMapping("/alipay/prepay")
    public Result<String> prepay(@RequestBody PayVo pay);
    @PostMapping("/alipay/refund")
    Result<Boolean> refund(@RequestBody RefundVo refundVo);
}
