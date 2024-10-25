package com.tianji.exam.domain.dto;

import com.tianji.exam.constants.ExamType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(description = "查询题目实体")
public class QueryQuestionsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("课程ID")
    private Long courseId;

    @ApiModelProperty("章节ID")
    private Long sectionId;

    @ApiModelProperty("类型")
    private ExamType type;
}
