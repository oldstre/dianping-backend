package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {//注意是这里是不符合
            //2.校验不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }

        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);//随机生成六位长的验证码
        //4.保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);//业务前缀做区分，并设置有效期
        //5.发送验证码
        log.debug("发送验证码成功，验证码为：{}",code);//模拟发送验证码

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {//注意是这里是不符合
            //2.校验不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //2.校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cacheCode==null||!cacheCode.toString().equals(code)){
            //3.发的验证码和填的不一致，报错
            return Result.fail("验证码错误");
        }


        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user == null) {
            //6.不存在，创建新用户并保存
             user = createWithPhone(phone);
        }
        //7.保存用户信息到redis
        //7.1随机生成token,作为登录令牌
        String token  = UUID.randomUUID().toString();
        //7.2将user对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //这里由于stringRedisTemplate需要的键值对都是string类型，而UserDTO中的id是Long类型
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor((fileName,fileValue)-> fileValue.toString()));//相当于类型转换
        //7.3存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //7.4设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok();
    }

    @Override
    public Result sign() {
        //1.获取当前登录的用户
        Long id = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+id+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth()-1;
        //5.写入redis  的 SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth,true);
        return Result.ok();

    }

    @Override
    public Result signCount() {
        //1.获取当前登录的用户
        Long id = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+id+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth()-1;
        //5.获取本月截止今天的所有签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth + 1)).valueAt(0));
        if(result==null||result.isEmpty()){
            //没有签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num==null||num==0){
            return Result.ok(0);
        }
        //6.循环遍历
        int count=0;
        while (true){
            //6.1.让这个数字与1做与运算，得到数字的最后一个bit位 //6.2判断这个bit是否为0
            if((num&1)==0){
                //6.3如果为0，说明未签到，结束
                break;
            }
            else {
                //6.4如果不为零，说明已签到，计数器+1
                count++;
            }

            //6.5把最后一位右移一位，相当于抛弃最后一位，继续循环
            num>>>=1;
        }
        return Result.ok(count);
    }

    private User createWithPhone(String phone){
        //1.创建用户
        User user =new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));

        //2.保存用户
        save(user);
        return user;
    }
}
