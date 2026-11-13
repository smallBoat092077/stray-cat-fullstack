package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback {

    /** 功能建议 */
    public static final int TYPE_SUGGESTION = 0;
    /** 问题反馈 */
    public static final int TYPE_BUG = 1;
    /** 其他 */
    public static final int TYPE_OTHER = 2;

    /** 待处理 */
    public static final int STATUS_PENDING = 0;
    /** 已回复 */
    public static final int STATUS_REPLIED = 1;
    /** 已忽略 */
    public static final int STATUS_IGNORED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** TYPE_SUGGESTION / TYPE_BUG / TYPE_OTHER */
    private Integer type;

    private String content;

    /** 截图相对路径，逗号分隔，最多 3 张 */
    private String images;

    /** STATUS_PENDING / STATUS_REPLIED / STATUS_IGNORED */
    private Integer status;

    /** 管理员回复内容 */
    private String reply;

    private LocalDateTime repliedAt;

    private LocalDateTime createdAt;

    /** 提交人昵称，后台列表展示用，不落库 */
    @TableField(exist = false)
    private String nickname;
}
