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

    // 账号安全相关 1xxx
    LOGIN_TEMP_LOCKED(1008, "账号已临时锁定，请稍后再试"),
    LOGIN_PERMANENT_LOCKED(1009, "账号已被锁定，请联系管理员"),
    PASSWORD_SAME(1010, "新密码不能与当前密码相同"),
    PASSWORD_CONFIRM_MISMATCH(1011, "两次输入的新密码不一致"),
    EMAIL_INVALID(1012, "邮箱格式不正确"),
    EMAIL_CODE_INVALID(1013, "验证码错误或已过期"),
    EMAIL_SEND_TOO_FAST(1014, "发送过于频繁，请稍后再试"),
    EMAIL_NOT_BOUND(1015, "该用户未绑定验证邮箱，无法通过邮件重置"),

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
    XPAY_WX_LOGIN_REQUIRED(3011, "请先在「我的」页面绑定微信后再充值"),
    CONTENT_RISKY(3012, "内容包含违规信息，请修改后重新提交"),
    CONTENT_CHECK_UNAVAILABLE(3013, "内容安全校验暂不可用，请稍后再试"),
    WECHAT_ALREADY_BOUND(3014, "当前账号已绑定微信"),
    WECHAT_BIND_CONFLICT(3015, "该微信号已绑定其他账号，请先用微信登录确认"),
    WECHAT_CODE_INVALID(3016, "微信登录凭证无效，请重试"),
    
    // 系统相关 9xxx
    SYSTEM_ERROR(9001, "系统异常"),
    SERVICE_UNAVAILABLE(9002, "服务不可用");

    private final Integer code;
    private final String message;
}
