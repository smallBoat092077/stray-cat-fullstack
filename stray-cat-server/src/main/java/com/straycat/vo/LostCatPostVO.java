package com.straycat.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 寻猫启事对外视图。联系方式按审核状态与 contactWay 脱敏后下发，
 * 不下发未经审核的手机号；inapp 只走站内消息，不暴露联系方式。
 */
@Data
public class LostCatPostVO {

    private Long id;

    private Long userId;

    private String catName;

    private String coatDesc;

    private List<String> images;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lostAt;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String addressDesc;

    /** 归属省/市/区码（国标，区域划分需求 R5；null = 未归属，后台据此显示与修正） */
    private String provinceCode;

    private String cityCode;

    private String districtCode;

    /** 省/市/区名称（region 字典翻译；详情接口对未归属的老数据用逆地理编码兜底） */
    private String provinceName;

    private String cityName;

    private String districtName;

    private String contactWay;

    /** 脱敏后的联系方式，可能为空 */
    private String contactValue;

    /** 0 寻找中 / 1 已找到 / 2 已关闭 */
    private Integer status;

    /** 0 待审 / 1 通过 / 2 驳回 */
    private Integer auditStatus;

    /** 驳回原因（我的发布可见） */
    private String auditRemark;

    private Integer viewCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
