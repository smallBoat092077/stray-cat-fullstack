package com.straycat.vo;

import com.straycat.entity.ShareLink;
import lombok.Data;

/**
 * /share/resolve 的下发白名单。inviterId、status、ownerRole 不下发到小程序端。
 */
@Data
public class ShareLinkVO {

    private String sid;
    private String channel;
    private String campaign;
    private String bizType;
    private Long bizId;
    private String landingPage;

    public static ShareLinkVO of(ShareLink link) {
        if (link == null) {
            return null;
        }
        ShareLinkVO vo = new ShareLinkVO();
        vo.setSid(link.getSid());
        vo.setChannel(link.getChannel());
        vo.setCampaign(link.getCampaign());
        vo.setBizType(link.getBizType());
        vo.setBizId(link.getBizId());
        vo.setLandingPage(link.getLandingPage());
        return vo;
    }
}
