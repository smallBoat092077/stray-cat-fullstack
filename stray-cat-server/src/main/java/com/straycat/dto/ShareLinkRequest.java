package com.straycat.dto;

import lombok.Data;

/**
 * /share/link 入参白名单。inviterId / ownerRole / status 一律不接受客户端传值，
 * 邀请人只从服务端 token 反查，避免伪造他人分享。
 */
@Data
public class ShareLinkRequest {

    private String channel;
    private String campaign;
    private String bizType;
    private Long bizId;
    private String landingPage;
}
