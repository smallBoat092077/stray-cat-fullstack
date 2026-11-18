package com.straycat.service;

import com.straycat.entity.ShareLink;

import java.util.List;
import java.util.Map;

public interface ShareLinkService {

    /**
     * 签发渠道短码。inviterId 为 null 表示官方物料或匿名分享。
     */
    ShareLink createLink(String channel, String campaign, String bizType, Long bizId,
                         String landingPage, Long inviterId);

    /**
     * 解析有效短码；不存在、停用、过期一律返回 null，由调用方降级为 direct。
     */
    ShareLink resolve(String sid);

    /**
     * 首触归因写 sys_user 三列。只在用户创建时调用，channel 以服务端表为准。
     */
    void applyAttribution(com.straycat.entity.SysUser user, com.straycat.dto.AttributionParam param);

    /**
     * 记录一次可归属到用户的访问，uk_sid_user 保证幂等。
     */
    void recordVisit(String sid, Long userId, boolean isNewUser, String entryType, String sceneValue);

    String userSourceOf(String channel);

    Map<String, Object> channelStats(int days);

    List<Map<String, Object>> linkList(int days);

    boolean tryAcquireQuota(String clientIp);
}
