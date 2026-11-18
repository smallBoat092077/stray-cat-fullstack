package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.UserSecurityEvent;
import com.straycat.vo.SecurityStatusVO;

/**
 * 账号安全：阶梯锁定状态机 + 自主改密 + 邮箱绑定/解绑 + 安全事件。
 */
public interface UserSecurityService {

    /** 密码登录前的锁定校验；24 小时到期会在这里自动解锁并清零 */
    void checkPasswordLoginAllowed(String username);

    /** 微信登录校验：临时锁放行，仅永久锁拒绝 */
    void checkWechatLoginAllowed(String username);

    /** 记录一次密码校验失败（可能触发临时锁 / 永久锁），总是抛出对应的 BusinessException */
    void recordFailure(String username, Long userId);

    /** 密码校验成功：计数清零、解除临时锁 */
    void recordSuccess(String username, Long userId);

    void changePassword(Long userId, String currentPassword, String newPassword, String confirmPassword);

    SecurityStatusVO securityStatus(Long userId);

    void sendEmailCode(Long userId, String email, String scene);

    void bindEmail(Long userId, String email, String code);

    void unbindEmail(Long userId, String password);

    /** 管理员手动解锁（不改密码、不恢复 status） */
    void adminUnlock(Long userId);

    /** 管理员重置密码后自动解锁 + ADMIN_PWD_RESET 事件 + 通知邮件 */
    void resetUnlock(Long userId);

    /**
     * 方案 C：管理员发起「邮件验证码自助重置」。
     * 生成一次性重置码（30 分钟有效）发送到用户已验证邮箱；不改密码、不踢会话。
     * 返回脱敏邮箱供后台展示；用户未绑定验证邮箱时抛 1015。
     */
    String issuePasswordResetCode(Long userId);

    /**
     * 方案 C：用户凭重置码自助设置新密码（免登）。
     * 校验通过后：设新密码 → 自动解锁 → 踢下线 → 写 PWD_RESET_BY_CODE 事件 → 删码 → 确认邮件。
     * 重置码错 5 次即作废；永久锁账号同样可通过此通道恢复。
     */
    void resetPasswordByCode(String username, String code, String newPassword);

    /** userKey 传数字时按 userId 精确查，传非数字时按 username 模糊查（对应原型「账号 / 用户ID」输入框） */
    Page<UserSecurityEvent> pageEvents(String userKey, String eventType, Integer pageNum, Integer pageSize);
}
