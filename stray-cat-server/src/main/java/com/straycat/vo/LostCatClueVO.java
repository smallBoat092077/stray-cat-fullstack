package com.straycat.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 寻猫线索对外视图。绝不返回 reporterId 明细、手机号、openid 或任何联系方式；
 * images 保持逗号分隔字符串（与落库一致），由小程序 / 后台自行拼全地址。
 */
@Data
public class LostCatClueVO {

    private Long id;

    private Long postId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime foundAt;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String addressDesc;

    /** 逗号分隔 /uploads/...，最多 6 张 */
    private String images;

    private String description;

    /** 0 待审 / 1 通过 / 2 驳回 */
    private Integer auditStatus;

    /** 驳回原因（我的线索可见） */
    private String auditRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
