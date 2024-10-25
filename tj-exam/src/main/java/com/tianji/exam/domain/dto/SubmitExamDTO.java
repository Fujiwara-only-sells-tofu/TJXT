package com.tianji.exam.domain.dto;

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
@ApiModel(description = "提交考试结果实体")
public class SubmitExamDTO implements Serializable {

    private static final long serialVersionUID = 1L;



    @ApiModelProperty("考试记录id")
    private Long id; // 考试记录id

    @ApiModelProperty("考试详情列表")
    private List<ExamDetailDTO> examDetails; // 题目详情列表
}
