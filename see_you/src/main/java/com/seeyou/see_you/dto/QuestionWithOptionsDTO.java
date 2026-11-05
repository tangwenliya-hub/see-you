package com.seeyou.see_you.dto;

import com.seeyou.see_you.entity.PsyOption;
import lombok.Data;
import java.util.List;

@Data
public class QuestionWithOptionsDTO {
    private Long questionId;
    private Long testId;
    private String questionContent;
    private List<PsyOption> options;
}
