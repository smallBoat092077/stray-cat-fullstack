package com.straycat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.dto.AttributionParam;
import com.straycat.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

    /**
     * 微信登录。保留无参版本给已有调用方；带归因的版本供 /user/login 使用。
     */
    SysUser wxLogin(String code);

    SysUser wxLogin(String code, AttributionParam attribution);

    /**
     * 根据openid查询用户
     */
    SysUser getByOpenid(String openid);

    /**
     * 账号密码注册。同样保留无参版本。
     */
    SysUser register(String username, String rawPassword);

    SysUser register(String username, String rawPassword, AttributionParam attribution);

    /**
     * 账号密码登录
     */
    SysUser loginByPassword(String username, String rawPassword);

    /**
     * 授予或撤销角色，只有管理员能操作，且不能改自己
     */
    void grantRole(Long userId, Long operatorId, Integer role);

    /**
     * 账号密码用户绑定微信：用 wx.login 的 code 换 openid 并落到当前账号上，
     * 绑定后才能走微信虚拟支付充值和 msgSecCheck 内容检测
     */
    SysUser bindWechat(Long userId, String code);
}
