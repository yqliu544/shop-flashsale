package cn.wolfcode.config;

import cn.wolfcode.util.IdGenerateUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public IdGenerateUtil idGenerateUtil(){
        return new IdGenerateUtil(0,1);
    }
}
