package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("seckill-service")
public interface SeckillFeignService {
    @PostMapping("/orderPay/success")
    Result<?> updateOrderPaySuccess(@RequestBody PayResult payResult);
}
