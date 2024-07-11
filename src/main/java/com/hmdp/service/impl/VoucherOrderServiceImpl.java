package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
       // 1. 查询特价卷信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2. 判断秒杀是否开始
        if(LocalDateTime.now().isBefore(voucher.getBeginTime())){
            return Result.fail("秒杀活动未开始");
        }

        if(LocalDateTime.now().isAfter(voucher.getEndTime())){
            return Result.fail("秒杀活动已经结束");
        }

        // 3. 判断库存是否充足
        if(voucher.getStock() < 0){
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        /**
         * 自己写的分布式锁
         */
//        // 创建锁对象(新增代码)
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//
//        // 获取锁对象
//        boolean isLock = lock.tryLock(1200);

        /**
         * 使用redisson创建分布式锁
         */
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // boolean tryLock(), 默认时间为1s, 30s过期
        // boolean tryLock(long time, TimeUnit unit)
        // boolean tryLock(long var1, long var3, TimeUnit var5)
        boolean isLock = lock.tryLock();

        if(!isLock){
            return Result.fail("不允许重复下单");
        }

        synchronized (userId.toString().intern()){
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }

    }

    @Transactional
    public synchronized Result createVoucherOrder(Long voucherId){
        // 4. 一人一单逻辑
        // 4.1 用户id
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 4.2 判断是否存在
        if(count > 0){
            // 用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }

        // 5. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")  // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update(); // where id = ? and stock > 0

        if(!success){
            return Result.fail("库存不足");
        }

        // 6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1 创建订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2 设置用户id
        voucherOrder.setUserId(userId);
        // 6.3 设置优惠卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
