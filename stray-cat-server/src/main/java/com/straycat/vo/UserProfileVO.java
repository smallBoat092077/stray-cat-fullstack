package com.straycat.vo;

import com.straycat.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本人可见的用户信息（白名单）。
 *
 * <p>不要在这里加字段就完事——只有客户端确实要用的才允许放进来。
 * 密码、session_key、身份证、真实姓名、手机号、unionId 一律不出现，
 * 将来给 sys_user 加新的敏感列也不会因为「忘了加注解」而被序列化出去。
 */
@Data
public class UserProfileVO {

    private Long id;

    /** 账号登录名；微信登录用户为 null，前端据此显示「微信登录」 */
    private String username;

    private String nickname;

    private String avatarUrl;

    /**
     * 当前小程序内的用户标识。
     * 过渡字段：小程序（已发布的体验版）用 openid 是否为空来判断要不要显示「绑定微信」入口，
     * 等前端切换到 wechatBound 后这个字段可以删掉。
     */
    private String openid;

    /** 是否已绑定微信；新前端用这个，语义比 openid 更明确 */
    private Boolean wechatBound;

    /** 角色: 0普通用户 1志愿者 2领养人 3管理员 */
    private Integer role;

    private Integer creditScore;

    private Integer coinBalance;

    /** 志愿者等级 1~6 */
    private Integer volunteerLevel;

    /** 状态: 0禁用 1正常 */
    private Integer status;

    private LocalDateTime createdAt;

    public static UserProfileVO of(SysUser user) {
        if (user == null) {
            return null;
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        String openid = user.getOpenid();
        vo.setOpenid(openid);
        vo.setWechatBound(openid != null && !openid.isBlank());
        vo.setRole(user.getRole());
        vo.setCreditScore(user.getCreditScore());
        vo.setCoinBalance(user.getCoinBalance());
        vo.setVolunteerLevel(user.getVolunteerLevel());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
