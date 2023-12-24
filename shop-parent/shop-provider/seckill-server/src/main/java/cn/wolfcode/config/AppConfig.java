package cn.wolfcode.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Configuration
public class AppConfig {
    @Bean
    public ScheduledExecutorService scheduledExecutorService(){
        ScheduledThreadPoolExecutor service = new ScheduledThreadPoolExecutor(10);
        return service;
    }
}
