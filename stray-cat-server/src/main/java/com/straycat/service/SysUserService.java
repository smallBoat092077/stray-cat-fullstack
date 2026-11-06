package com.straycat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

    /**
     * 微信登录
     */
    SysUser wxLogin(String code);

    /**
     * 根据openid查询用户
     */
    SysUser getByOpenid(String openid);

    /**
     * 账号密码注册
     */
    SysUser register(String username, String rawPassword);

    /**
     * 账号密码登录
     */
    SysUser loginByPassword(String username, String rawPassword);

    /**
     * 授予或撤销角色，只有管理员能操作，且不能改自己
     */
    void grantRole(Long userId, Long operatorId, Integer role);
}
