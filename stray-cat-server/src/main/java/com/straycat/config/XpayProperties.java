package com.straycat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟支付配置。appKey 只能存在于服务端，切勿下发到小程序包内。
 * productId 与 priceFen 必须与小程序后台「虚拟支付 - 道具管理」中已发布道具完全一致。
 */
@Data
@Component
@ConfigurationProperties(prefix = "xpay")
public class XpayProperties {

    private String offerId = "";

    private String appKey = "";

    /**
     * 0 现网，1 沙箱。iOS 不支持沙箱。
     */
    private Integer env = 0;

    /**
     * 消息推送「消息加解密密钥/Token」，用于校验回调来源
     */
    private String pushToken = "";

    /**
     * 未接通后台时也不要把对账任务打到微信接口上
     */
    private Boolean reconcileEnabled = false;

    private List<Pack> packs = new ArrayList<>();

    public boolean ready() {
        return offerId != null && !offerId.isBlank()
                && appKey != null && !appKey.isBlank()
                && !packs.isEmpty();
    }

    public Pack findPack(String code) {
        if (code == null) {
            return null;
        }
        for (Pack pack : packs) {
            if (code.equals(pack.getCode())) {
                return pack;
            }
        }
        return null;
    }

    @Data
    public static class Pack {

        private String code;

        private String productId;

        /**
         * 到账猫粮数量
         */
        private Integer coinAmount;

        /**
         * 道具价格（分），需与后台道具定价一致
         */
        private Integer priceFen;

        private String title;
    }
}
