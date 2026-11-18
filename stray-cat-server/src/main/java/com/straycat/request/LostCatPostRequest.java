package com.straycat.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发布寻猫启事表单。文本字段统一走 ContentSecurityService.check()。
 */
@Data
public class LostCatPostRequest {

    private String catName;

    private String coatDesc;

    /** 逗号分隔的 /uploads/... 相对路径，最多 6 张 */
    private String images;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lostAt;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String addressDesc;

    /** 发布时归属省/市/区（国标行政区码，区域划分需求 R2）；端侧解析失败时后端按坐标兜底补码 */
    private String provinceCode;

    private String cityCode;

    private String districtCode;

    /** inapp / phone，默认站内消息 */
    private String contactWay;

    private String contactValue;
}
