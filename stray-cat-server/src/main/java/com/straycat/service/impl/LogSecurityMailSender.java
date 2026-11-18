package com.straycat.service.impl;

import com.straycat.service.SecurityMailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟发信实现（默认）：不真正投递，只把完整邮件（含验证码）打到日志。
 * 验收时从日志里搜索「邮箱验证验证码」或「SECURITY-MAIL」即可取到验证码。
 * 激活条件：security.mail.provider=log（缺省即模拟），与 smtp / ses 实现互斥。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "security.mail", name = "provider", havingValue = "log", matchIfMissing = true)
public class LogSecurityMailSender implements SecurityMailSender {

    @Override
    public void send(String to, String subject, String content) {
        log.info("========== SECURITY-MAIL 模拟发送 ==========");
        log.info("收件人: {}", to);
        log.info("主题: {}", subject);
        log.info("正文: {}", content);
        log.info("===========================================");
    }
}
