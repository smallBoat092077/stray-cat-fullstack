package com.straycat.util;

/**
 * 公开展示的用户信息脱敏规则。UGC 列表（打赏榜、评论区）统一走这里，
 * 避免昵称原文出现在任何响应里。
 */
public final class PrivacyMask {

    private static final String FALLBACK_NICKNAME = "爱心网友";

    private PrivacyMask() {
    }

    public static String nickname(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return FALLBACK_NICKNAME;
        }
        if (nickname.length() == 1) {
            return nickname + "**";
        }
        return nickname.charAt(0) + "**";
    }
}
