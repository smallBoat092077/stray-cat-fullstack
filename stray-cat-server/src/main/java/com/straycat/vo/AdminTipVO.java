package com.straycat.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminTipVO {

    private Long id;

    private Long catId;

    private String catName;

    private Long fromUserId;

    /**
     * 后台看全昵称，小程序公开列表里仍然是脱敏的
     */
    private String nickname;

    private Integer coinAmount;

    private String message;

    private LocalDateTime createdAt;
}
