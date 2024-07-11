package com.hmdp;

import com.hmdp.config.RedissonConfig;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private RedissonClient redissonClient;

    @Test
    void testSavaShop(){
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testRedisson() throws InterruptedException {
        // 获取锁（可重入）,指定锁的名称
        RLock lock = redissonClient.getLock("lsm_lock");

        // 尝试获取锁，参数分别是：获取锁的最大等待时间(期间会重试)，锁自动释放时间，时间单位

        boolean isLock = lock.tryLock(1, 100, TimeUnit.SECONDS);

        if(isLock){
            try {
                System.out.println("执行业务");
            } finally {
                System.out.println("开始释放锁，强制关闭........................");
                lock.unlock();
            }
        }


    }

}
