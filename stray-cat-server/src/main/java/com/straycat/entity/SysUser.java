package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String openid;

    /**
     * 虚拟支付的用户态签名密钥，不能随用户信息下发
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String sessionKey;

    /**
     * 跨应用的稳定身份标识，只用于服务端归并账号，绝不随响应下发。
     * 响应统一走 UserProfileVO / UserAdminVO / UserPublicVO 白名单，这里是第二道保险。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String unionId;

    private String nickname;

    private String avatarUrl;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String phone;

    /**
     * 角色: 0普通用户 1志愿者 2领养人 3管理员
     */
    private Integer role;

    /**
     * 信用积分
     */
    private Integer creditScore;

    /**
     * 猫粮余额（站内虚拟积分，无现金价值、不可兑换）
     */
    private Integer coinBalance;

    /**
     * 志愿者等级 1~6，由已完成打卡数推导，只升不降
     */
    private Integer volunteerLevel;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String realName;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String idCard;

    /**
     * 状态: 0禁用 1正常
     */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
