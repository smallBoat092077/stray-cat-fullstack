package com.straycat.dto;

import lombok.Data;

/** 单题作答：questionId + 打乱后的选项下标 */
@Data
public class QuizAnswerDTO {

    private Long questionId;

    private Integer optionIndex;
}
