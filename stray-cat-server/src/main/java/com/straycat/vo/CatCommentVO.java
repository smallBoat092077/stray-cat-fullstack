package com.straycat.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CatCommentVO {

    private Long id;

    private Long catId;

    /** 0=顶级评论 */
    private Long parentId;

    private String content;

    /** 脱敏后的昵称，和打赏榜一套规则 */
    private String nickname;

    /** 头像原始值，前端用 resolveImageUrl 补全 */
    private String avatar;

    /** 被回复人的脱敏昵称，顶级评论为 null */
    private String replyToNickname;

    private LocalDateTime createdAt;

    /** 该评论下的回复总数（顶级评论才有意义） */
    private Integer replyCount;

    /** 预览回复，最多前 3 条；升序 */
    private List<CatCommentVO> replies;

    /** 是否当前登录用户所发，前端据此显示删除入口 */
    private Boolean mine;
}
