package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 寻猫启事（方案 4.2）。先审后发；不设赏金/悬赏/支付字段，
 * 联系方式默认走站内消息，公开展示手机号需审核且脱敏。
 */
@Data
@TableName("lost_cat_post")
public class LostCatPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布人 */
    private Long userId;

    /** 猫咪名字 */
    private String catName;

    /** 毛色 / 花纹描述（为二期花纹识别预留） */
    private String coatDesc;

    /** 逗号分隔 /uploads/...，最多 6 张 */
    private String images;

    /** 丢失时间 */
    private LocalDateTime lostAt;

    /** 丢失地点纬度 */
    private BigDecimal latitude;

    /** 丢失地点经度 */
    private BigDecimal longitude;

    /** 地点文字 */
    private String addressDesc;

    /** 发布时归属省/市/区（国标行政区码，region 字典同源；区域划分需求 R2），全 null = 未归属 */
    private String provinceCode;

    private String cityCode;

    private String districtCode;

    /** inapp 站内消息 / phone 电话 */
    private String contactWay;

    /** 联系方式，公开展示前脱敏 */
    private String contactValue;

    /** 0 寻找中 / 1 已找到 / 2 已关闭 */
    private Integer status;

    /** 0 待审 / 1 通过 / 2 驳回 */
    private Integer auditStatus;

    /** 驳回原因 */
    private String auditRemark;

    /** 审核人 */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 浏览次数 */
    private Integer viewCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
