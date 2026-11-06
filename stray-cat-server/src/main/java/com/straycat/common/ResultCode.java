package com.straycat.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    
    // 用户相关 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_DISABLED(1002, "用户已被禁用"),
    LOGIN_FAILED(1003, "登录失败"),
    TOKEN_EXPIRED(1004, "Token已过期"),
    TOKEN_INVALID(1005, "Token无效"),
    UNAUTHORIZED(1006, "未授权"),
    FORBIDDEN(1007, "无权限"),
    
    // 参数相关 2xxx
    PARAM_ERROR(2001, "参数错误"),
    PARAM_MISSING(2002, "参数缺失"),
    
    // 业务相关 3xxx
    CAT_NOT_FOUND(3001, "猫咪不存在"),
    TASK_NOT_FOUND(3002, "任务不存在"),
    TASK_ALREADY_ACCEPTED(3003, "任务已被接单"),
    CHECKIN_DISTANCE_TOO_FAR(3004, "距离投喂点过远"),
    ADOPTION_ALREADY_APPLIED(3005, "已提交领养申请"),
    COIN_NOT_ENOUGH(3006, "猫粮余额不足"),
    COIN_AMOUNT_INVALID(3007, "猫粮数量不合法"),
    RECHARGE_PACK_INVALID(3008, "充值礼包不存在或已下架"),
    RECHARGE_ORDER_NOT_FOUND(3009, "充值订单不存在"),
    XPAY_NOT_CONFIGURED(3010, "虚拟支付尚未开通"),
    XPAY_WX_LOGIN_REQUIRED(3011, "请使用微信登录后再充值"),
    CONTENT_RISKY(3012, "内容包含违规信息，请修改后重新提交"),
    CONTENT_CHECK_UNAVAILABLE(3013, "内容安全校验暂不可用，请稍后再试"),
    
    // 系统相关 9xxx
    SYSTEM_ERROR(9001, "系统异常"),
    SERVICE_UNAVAILABLE(9002, "服务不可用");

    private final Integer code;
    private final String message;
}
