package com.straycat.common;

/**
 * 站内消息类型。templateKey 对应 wechat.subscribe.templates 下的配置项，
 * 该配置为空时只写站内消息、不调微信订阅消息接口。
 */
public enum MessageType {

    /** 有人接了你发起的投喂任务 */
    TASK_ACCEPTED("taskAccepted", "投喂任务已被接单", "/pages/feeding/detail?id=%d"),
    /** 打卡审核通过 */
    RECORD_APPROVED("recordApproved", "投喂打卡已通过", "/pages/feeding/records"),
    /** 打卡被驳回，退回已接单等重新打卡 */
    RECORD_REJECTED("recordRejected", "投喂打卡被驳回", "/pages/feeding/detail?id=%d"),
    /** 志愿者已打卡，等待管理员审核（发给打赏发起人） */
    RECORD_SUBMITTED("recordSubmitted", "你委托的投喂已打卡", "/pages/feeding/detail?id=%d"),
    /** 领养申请通过 */
    ADOPTION_APPROVED("adoptionApproved", "领养申请已通过", "/pages/adoption/list"),
    /** 领养申请被拒 */
    ADOPTION_REJECTED("adoptionRejected", "领养申请未通过", "/pages/adoption/list"),
    /** 到了该提交回访的节点 */
    FOLLOW_UP_DUE("followUpDue", "该提交领养回访了", "/pages/adoption/list"),
    /** 有人回复了你的评论，bizId 是猫咪 id，站内没有对应订阅模板，只落消息中心 */
    COMMENT_REPLY("commentReply", "有人回复了你的评论", "/pages/cat/detail?id=%d"),
    /** 新的领养申请等待审核（发给管理员） */
    ADOPTION_NEW("adoptionNew", "有新的领养申请待审核", "/pages/admin/review"),
    /** 新的打卡记录等待审核（发给管理员） */
    RECORD_PENDING("recordPending", "有新的投喂打卡待审核", "/pages/admin/review");

    private final String templateKey;
    private final String title;
    private final String pagePattern;

    MessageType(String templateKey, String title, String pagePattern) {
        this.templateKey = templateKey;
        this.title = title;
        this.pagePattern = pagePattern;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public String getTitle() {
        return title;
    }

    /**
     * 小程序内跳转路径，pagePattern 里的 %d 用业务 id 填充；没有 id 时保留列表页
     */
    public String page(Long bizId) {
        if (!pagePattern.contains("%d") || bizId == null) {
            return pagePattern.replace("%d", "0");
        }
        return String.format(pagePattern, bizId);
    }
}
