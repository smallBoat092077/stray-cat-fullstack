package com.straycat.vo;

import lombok.Data;

import java.util.List;

/**
 * 对外抽题视图。只有题干与打乱后的选项，绝不包含 answerIndex / analysis。
 */
@Data
public class WikiQuestionVO {

    private Long id;

    private String category;

    private String title;

    private List<String> options;
}
