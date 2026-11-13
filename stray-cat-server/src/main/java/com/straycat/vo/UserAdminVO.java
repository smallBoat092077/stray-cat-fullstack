package com.straycat.vo;

import com.straycat.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台用户列表行（白名单）。
 *
 * <p>列是给管理员做角色管理和对账用的，只需要这几项。
 * openid / unionId / session_key / 证件号属于身份凭证，管理员列表里没有展示需求，
 * 一律不下发，避免「管理员账号被撞库 = 全站用户身份标识一起丢」。
 */
@Data
public class UserAdminVO {

    private Long id;

    private String nickname;

    private String username;

    private String avatarUrl;

    /** 角色: 0普通用户 1志愿者 2领养人 3管理员 */
    private Integer role;

    private Integer creditScore;

    private Integer coinBalance;

    /** 志愿者等级 1~6 */
    private Integer volunteerLevel;

    /** 状态: 0禁用 1正常 */
    private Integer status;

    private LocalDateTime createdAt;

    public static UserAdminVO of(SysUser user) {
        if (user == null) {
            return null;
        }
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setUsername(user.getUsername());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRole(user.getRole());
        vo.setCreditScore(user.getCreditScore());
        vo.setCoinBalance(user.getCoinBalance());
        vo.setVolunteerLevel(user.getVolunteerLevel());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
