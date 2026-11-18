package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 寻猫线索（方案 4.2）。先审后发：audit_status=0 待审 / 1 通过 / 2 驳回。
 * 双方不暴露任何联系方式，表内不含手机号 / 微信号 / openid / userId 明细字段。
 */
@Data
@TableName("lost_cat_clue")
public class LostCatClue {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 lost_cat_post.id */
    private Long postId;

    /** 上报人 */
    private Long reporterId;

    /** 发现时间 */
    private LocalDateTime foundAt;

    /** 发现地点纬度 */
    private BigDecimal latitude;

    /** 发现地点经度 */
    private BigDecimal longitude;

    /** 地点文字 */
    private String addressDesc;

    /** 逗号分隔 /uploads/...，最多 6 张 */
    private String images;

    /** 线索描述 */
    private String description;

    /** 0 待审 / 1 通过 / 2 驳回 */
    private Integer auditStatus;

    /** 驳回原因 */
    private String auditRemark;

    /** 审核人 */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
