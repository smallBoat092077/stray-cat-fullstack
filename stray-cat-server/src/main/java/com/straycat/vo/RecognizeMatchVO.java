package com.straycat.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 识别候选（camelCase，前端已按此对齐）。Python 返回的 matches[].image_url 恒为空字符串，
 * catName / coverImage / color 由 Java 按 cat_id 反查 cat_profile 补全；查不到保留 catId、名字填「未知猫咪」。
 */
@Data
public class RecognizeMatchVO {

    private Long catId;

    private String catName;

    private String coverImage;

    private String color;

    private BigDecimal similarity;
}
