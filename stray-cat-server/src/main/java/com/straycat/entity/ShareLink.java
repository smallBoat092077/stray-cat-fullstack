package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道短码主表。公众号图文、微信分享卡片、小程序码、线下物料共用一张表，
 * 对外只暴露 sid，活动文案改了不用重发链接或重印物料。
 */
@Data
@TableName("share_link")
public class ShareLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sid;

    /** mp_article / mp_menu / mp_card / wxacode / share_card / offline */
    private String channel;

    /** 活动代号，如 home_diary_w1 */
    private String campaign;

    /** 分享人；官方物料与公众号为 null */
    private Long inviterId;

    /** cat / feeding / adoption */
    private String bizType;

    /** 落地对象 id，如 catId */
    private Long bizId;

    /** 已发布页面路径，不带 / 也不带 query，供 wxacode.getUnlimited 的 page 使用 */
    private String landingPage;

    /** 0 官方生成 1 用户分享生成 */
    private Integer ownerRole;

    /** 0 停用 1 正常 */
    private Integer status;

    /** null 表示长期有效 */
    private LocalDateTime expireAt;

    private LocalDateTime createdAt;
}
