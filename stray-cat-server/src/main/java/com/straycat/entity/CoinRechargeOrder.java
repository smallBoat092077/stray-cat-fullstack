package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 猫粮礼包充值订单。状态含义对齐微信 xpay 订单状态，便于对账。
 */
@Data
@TableName("coin_recharge_order")
public class CoinRechargeOrder {

    /** 已下单，未支付 */
    public static final int STATUS_CREATED = 0;
    /** 微信侧已支付，尚未加到用户余额 */
    public static final int STATUS_PAID = 1;
    /** 猫粮已到账 */
    public static final int STATUS_DELIVERED = 2;
    /** 已退款，猫粮应已回收 */
    public static final int STATUS_REFUNDED = 3;
    /** 订单关闭 */
    public static final int STATUS_CLOSED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String openid;

    /**
     * 商户业务订单号，全局唯一
     */
    private String outTradeNo;

    private String productId;

    private Integer coinAmount;

    /**
     * 实付金额（分）
     */
    private Integer priceFen;

    private Integer status;

    /**
     * 参与签名的原始 JSON 串，留档以便排查「签名不一致」
     */
    private String signData;

    /**
     * 微信订单号
     */
    private String wxOrderId;

    private LocalDateTime paidAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
