package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Result queryById(Long id) {
        // 1. 从redis中查询商铺缓存
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);

        // 2.未命中，返回空
        if(StrUtil.isBlank(shopStr)){
            return Result.fail("查询店铺不存在");
        }

        // 3. 命中，需要吧json反序列化未对象
        RedisData redisData = JSONUtil.toBean(shopStr, RedisData.class);
        Object data = redisData.getData();
        Shop shop = JSONUtil.toBean((JSONObject) data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断是否过期, 没有过期则直接返回店铺信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return Result.ok(shop);
        }

        // 5. 已过期，需要缓存重建，尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 6. 获取互斥锁成功，开启独立线程，查询数据库信息并存入reids中
        if(BooleanUtil.isTrue(isLock)){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 缓存重建
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        // 6. 无论是否获取锁都返回脏数据
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺Id不能为Null");
        }

        // 1. 更新数据库
        save(shop);

        // 2. 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok();
    }

    public Boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds){
        // 1. 查询店铺数据
        Shop shop = getById(id);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
