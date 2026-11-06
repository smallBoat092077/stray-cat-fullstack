package com.straycat.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CoinLedgerVO {

    private Long id;

    private Long userId;

    /**
     * 流水归属用户的昵称，账号已注销时为空
     */
    private String nickname;

    private Integer amount;

    private Integer balanceAfter;

    private String bizType;

    private Long bizId;

    private String remark;

    private LocalDateTime createdAt;
}
