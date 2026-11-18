package com.straycat.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 账号安全页状态展示；email 已脱敏，绝不返回密码相关信息 */
@Data
public class SecurityStatusVO {

    /** 0 正常 / 1 临时锁 / 2 永久锁 */
    private Integer lockStage;

    private LocalDateTime lockUntil;

    /** 脱敏邮箱，如 cat***@qq.com */
    private String email;

    /** 0 未绑定 / 1 已验证 */
    private Integer emailVerified;

    /** 是否已设置密码（纯微信用户为 false） */
    private Boolean hasPassword;

    private String username;
}
