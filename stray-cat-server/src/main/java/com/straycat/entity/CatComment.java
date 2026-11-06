package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cat_comment")
public class CatComment {

    /** 顶级评论的 parent_id 用 0 表示，避免 NULL 参与索引和比较 */
    public static final long ROOT = 0L;

    /** 1 正常 */
    public static final int STATUS_NORMAL = 1;

    /** 0 已删除（软删，保留审计线索） */
    public static final int STATUS_DELETED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long catId;

    private Long userId;

    /** 0=顶级评论，否则指向所属顶级评论；回复不再嵌套 */
    private Long parentId;

    /** 回复目标用户，用于「回复 @昵称」前缀和站内通知 */
    private Long replyToUserId;

    private String content;

    private Integer status;

    private LocalDateTime createdAt;
}
