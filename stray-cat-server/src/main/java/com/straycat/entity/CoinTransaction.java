package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coin_transaction")
public class CoinTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 正数为获得，负数为支出
     */
    private Integer amount;

    private Integer balanceAfter;

    /**
     * REGISTER SIGNIN REPORT CHECKIN FOLLOWUP TIP
     */
    private String bizType;

    /**
     * 业务主键，与 userId+bizType 组成唯一索引用于幂等发奖；签到用 yyyyMMdd
     */
    private Long bizId;

    private String remark;

    private LocalDateTime createdAt;
}
