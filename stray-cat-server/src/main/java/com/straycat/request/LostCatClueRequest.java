package com.straycat.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 寻猫线索上报表单。文本字段统一走 ContentSecurityService.check()。
 */
@Data
public class LostCatClueRequest {

    /** 关联启事 id（必填，且启事须已审核通过） */
    private Long postId;

    /** 发现时间，yyyy-MM-dd HH:mm:ss（无 T、带秒） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime foundAt;

    private BigDecimal latitude;

    private BigDecimal longitude;

    /** 地点文字 */
    private String addressDesc;

    /** 逗号分隔的 /uploads/... 相对路径，最多 6 张 */
    private String images;

    /** 线索描述（必填） */
    private String description;
}
