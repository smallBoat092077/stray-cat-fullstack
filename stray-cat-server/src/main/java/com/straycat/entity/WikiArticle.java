package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 猫咪百科 - 科普文章。
 */
@Data
@TableName("wiki_article")
public class WikiArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String summary;

    private String content;

    private String coverUrl;

    /** feeding/health/behavior/hygiene/firstaid */
    private String category;

    /** 逗号分隔标签 */
    private String tags;

    private Integer readMinutes;

    /** 0草稿 1发布 */
    private Integer status;

    private Integer sort;

    private Integer viewCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
