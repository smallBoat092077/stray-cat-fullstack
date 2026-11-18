package com.straycat.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuizSubmitDTO {

    private List<QuizAnswerDTO> answers;
}
