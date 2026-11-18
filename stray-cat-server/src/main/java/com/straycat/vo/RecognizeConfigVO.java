package com.straycat.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 打卡页 onLoad 拉取的灰度配置（GET /feeding/recognize/config，匿名放行） */
@Data
public class RecognizeConfigVO {

    private boolean enabled;

    private BigDecimal matchThreshold;

    private BigDecimal manualThreshold;

    private int topK;
}
