package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Mr.Lu
 * @version 1.0
 * @date 2024-07-10 20:45
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379")
                .setPassword("123456");
        System.out.println("78236487734853");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
