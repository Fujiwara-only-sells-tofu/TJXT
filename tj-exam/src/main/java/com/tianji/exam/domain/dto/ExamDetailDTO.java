package com.tianji.exam.domain.dto;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(description = "题目详情实体")
public class ExamDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("题目id")
    private String questionId; // 题目id

    @ApiModelProperty("题目类型")
    private int questionType; // 题目类型

    @ApiModelProperty("题目答案")
    private String answer; // 题目答案
}
