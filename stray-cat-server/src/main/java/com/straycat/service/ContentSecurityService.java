package com.straycat.service;

public interface ContentSecurityService {

    /**
     * 微信 msg_sec_check 的场景值，用于让模型选择对应的检测策略
     */
    int SCENE_PROFILE = 1;

    int SCENE_COMMENT = 2;

    int SCENE_SOCIAL = 4;

    /**
     * 检测一段 UGC，命中违规直接抛 BusinessException。
     *
     * @param userId 提交者，用来取 openid（微信要求该用户近两小时内访问过小程序）
     * @param scene  本接口的场景值
     * @param parts  该次提交里的所有自由文本字段，会合并成一次检测
     */
    void check(Long userId, int scene, String... parts);
}
