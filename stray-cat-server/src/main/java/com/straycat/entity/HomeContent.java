package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页内容位：热点轮播 + 系统通告统一表（方案 4.1）。
 * 两类内容同为「运营发布 + 上下架时间窗 + 站内跳转」，共用一套 CRUD。
 */
@Data
@TableName("home_content")
public class HomeContent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** HOTSPOT 热点 / NOTICE 通告 */
    private String type;

    /** 热点标题 / 通告标题 */
    private String title;

    /** 副标题 / 通告正文摘要 */
    private String summary;

    /** 热点封面，NOTICE 可空 */
    private String coverUrl;

    /** 角标文案，如「热点」「公告」 */
    private String tag;

    /** campaign / cat / page / none */
    private String linkType;

    /** 站内路径或业务参数，须过 LINK_PATTERN */
    private String linkUrl;

    /** 升序；同序按 created_at 倒序 */
    private Integer sortOrder;

    /** 通告置顶 */
    private Integer pinned;

    /** 0 下线 / 1 上线 */
    private Integer status;

    /** 生效开始时间，NULL 不限 */
    private LocalDateTime startTime;

    /** 生效结束时间，NULL 不限 */
    private LocalDateTime endTime;

    /** 点击/阅读数 */
    private Integer pv;

    /** 管理员 id */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
