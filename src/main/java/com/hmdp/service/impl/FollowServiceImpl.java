package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result follow(Long id, Boolean isFollow) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //1.判断是关注还是取关
        if(isFollow){
            //2.关注，新增数据
            Follow follow=new Follow();
            follow.setUserId(userId);
            follow.setUserId(id);
            save(follow);
        }
        else{
            //3.取关 删除 delete from tb_follow where userId=? and follow_user_id=?
            LambdaQueryWrapper<Follow> lambdaQueryWrapper=new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(Follow::getId,userId).eq(Follow::getUserId,id);
            remove(lambdaQueryWrapper);
        }


        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //1.查询是否关注 select count(*)  from tb_follow where userId=? and follow_user_id=?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        //判断
        return Result.ok(count>0);

    }
}
