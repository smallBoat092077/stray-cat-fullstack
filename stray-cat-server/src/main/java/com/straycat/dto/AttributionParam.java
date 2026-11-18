package com.straycat.dto;

import lombok.Data;

/**
 * 登录/注册请求携带的归因上下文。
 *
 * <p>只有 sid 是可信凭据：渠道、活动一律以后端 share_link 表为准，
 * 客户端传来的 channel/campaign 只用于排查，绝不直接落库，避免伪造活动数据。
 */
@Data
public class AttributionParam {

    private String sid;
    private String channel;
    private String campaign;
    private String entryType;
    private String sceneValue;

    public static AttributionParam of(String sid, String channel, String campaign, String entryType) {
        AttributionParam param = new AttributionParam();
        param.setSid(trimToNull(sid));
        param.setChannel(trimToNull(channel));
        param.setCampaign(trimToNull(campaign));
        param.setEntryType(trimToNull(entryType));
        return param;
    }

    public static AttributionParam of(String sid, String channel, String campaign,
                                      String entryType, String sceneValue) {
        AttributionParam param = of(sid, channel, campaign, entryType);
        param.setSceneValue(trimToNull(sceneValue));
        return param;
    }

    public boolean hasSid() {
        return sid != null && !sid.isEmpty();
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
