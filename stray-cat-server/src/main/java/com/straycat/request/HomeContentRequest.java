package com.straycat.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台首页内容位新增/编辑表单。linkUrl 在 Service 里过站内正则，拒绝外链。
 */
@Data
public class HomeContentRequest {

    private Long id;

    /** HOTSPOT / NOTICE */
    private String type;

    private String title;

    private String summary;

    private String coverUrl;

    private String tag;

    private String linkType;

    private String linkUrl;

    private Integer sortOrder;

    private Integer pinned;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
