package com.straycat.service;

/**
 * 账号安全邮件通道。UAT 用日志实现（验证码打到日志便于验收），生产用腾讯 SES 实现。
 * 所有实现都必须保证：邮件内容不含任何密码明文或临时密码。
 */
public interface SecurityMailSender {

    void send(String to, String subject, String content);
}
