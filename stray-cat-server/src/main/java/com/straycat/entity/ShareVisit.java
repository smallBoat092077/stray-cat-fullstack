package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归因明细。只记录能归属到具体用户的访问，匿名首访不落库（避免被刷），
 * uk_sid_user 保证同一用户对同一短码只记一次。
 */
@Data
@TableName("share_visit")
public class ShareVisit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sid;

    private Long visitUserId;

    /** 本次访问是否创建了新用户 */
    private Integer isNewUser;

    /** share_card / wxacode / mp_card / mp_article */
    private String entryType;

    /** 原始 scene，仅排查用 */
    private String sceneValue;

    private LocalDateTime createdAt;
}
