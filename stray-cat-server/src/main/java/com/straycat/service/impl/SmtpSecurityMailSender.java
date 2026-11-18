package com.straycat.service.impl;

import com.straycat.service.SecurityMailSender;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * QQ 邮箱 SMTP 发信实现（个人可用的真实通道）。
 *
 * <p>凭据只走环境变量，不落仓库：
 * <ul>
 *   <li>{@code MAIL_SMTP_USER}：QQ 邮箱地址，同时必须是发件人（QQ SMTP 强制一致）</li>
 *   <li>{@code MAIL_SMTP_AUTH_CODE}：QQ 邮箱 SMTP 授权码（不是 QQ 密码）</li>
 *   <li>{@code MAIL_FROM}：与授权邮箱保持一致</li>
 * </ul>
 * 激活条件：security.mail.provider=smtp。发信失败由调用方兜底（验证码场景清限频键允许重发）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "security.mail", name = "provider", havingValue = "smtp")
public class SmtpSecurityMailSender implements SecurityMailSender {

    private final JavaMailSender mailSender;

    @Value("${security.mail.from:}")
    private String from;

    @Value("${security.mail.from-name:猫遇猫舍安全中心}")
    private String fromName;

    public SmtpSecurityMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String to, String subject, String content) {
        // QQ SMTP 偶发 SSL 握手抖动（Remote host terminated the handshake，实测约 1/10 概率），
        // 首次失败自动重试一次；仍失败才抛给调用方走各自的兜底逻辑。
        try {
            doSend(to, subject, content);
        } catch (Exception first) {
            log.warn("SECURITY-MAIL SMTP 首次发送失败，重试一次: to={} err={}", to, first.getMessage());
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("SMTP 发送失败: " + first.getMessage(), first);
            }
            try {
                doSend(to, subject, content);
            } catch (Exception second) {
                log.error("SECURITY-MAIL SMTP 重试仍失败: to={} subject={} err={}", to, subject, second.getMessage());
                throw new IllegalStateException("SMTP 发送失败: " + second.getMessage(), second);
            }
        }
        log.info("SECURITY-MAIL SMTP 已投递: to={} subject={}", to, subject);
    }

    private void doSend(String to, String subject, String content) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        // multipart=false：纯文本正文；helper 构造时已指定 UTF-8，主题与正文沿用该编码
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, false);
        mailSender.send(message);
    }
}
