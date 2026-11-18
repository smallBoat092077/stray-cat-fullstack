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
    /** 收到共同领养邀请 */
    CO_ADOPTION_INVITE("coAdoptionInvite", "收到共同领养邀请", "/pages/adoption/list"),
    /** 我发出的共同领养邀请有回应 */
    CO_ADOPTION_RESULT("coAdoptionResult", "共同领养邀请有回应", "/pages/adoption/list"),
    /** 新的打卡记录等待审核（发给管理员） */
    RECORD_PENDING("recordPending", "有新的投喂打卡待审核", "/pages/admin/review"),
    /** 用户提交了新反馈（发给管理员），跳小程序端反馈处理页，?id 直达对应那条 */
    FEEDBACK_NEW("feedbackNew", "收到新的用户反馈", "/pages/admin/feedback?id=%d"),
    /** 管理员回复了用户的反馈（发给反馈人） */
    FEEDBACK_REPLIED("feedbackReplied", "你的反馈有回复了", "/pages/feedback/index"),

    /** 活动提醒：给访问过某活动的用户发的运营触达，bizId 是活动 id，点消息回活动页 */
    CAMPAIGN_REMINDER("campaignReminder", "活动提醒", "/pages/campaign/index?id=%d"),

    /** 渠道日报：每天凌晨汇总昨日分享/新增数据发给管理员，只落站内不接微信模板 */
    DAILY_REPORT("dailyReport", "渠道日报", "/pages/profile/index"),

    /** 寻猫线索审核通过（发给启事发布者）：bizId 是线索 id，点消息跳线索详情页 */
    LOST_CAT_CLUE_NEW("lostCatClueNew", "有人见过你的猫，快来看线索", "/pages/lost-cat/clue-detail?id=%d");

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
