package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLLock implements ILock{

    private String name;//锁名或者是业务名
    private StringRedisTemplate stringRedisTemplate;
    public static final String KEY_PREFIX ="lock:";
    //更新：
    public static final String ID_PREFIX = UUID.randomUUID().toString()+"-";

    public SimpleRedisLLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //更新：获取线程名称
        String id = ID_PREFIX+Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, id + "", timeoutSec, TimeUnit.SECONDS);
//        return success;如果这样写，success为null时，拆箱过程就会报空指针异常，所以用下面的方法，为null返回false
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //获取线程名称
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        //获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //判断标识是否一致
        if(threadId.equals(id)){
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }
    }
}
