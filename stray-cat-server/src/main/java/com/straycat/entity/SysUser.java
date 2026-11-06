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
