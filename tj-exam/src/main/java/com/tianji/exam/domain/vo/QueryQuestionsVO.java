package com.tianji.exam.domain.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(description = "查询考试题目数据")
public class QueryQuestionsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "考试记录id")
    private Long id;

    @ApiModelProperty(value = "考试题目列表")
    List<QuestionDetailVO> questions;

}
