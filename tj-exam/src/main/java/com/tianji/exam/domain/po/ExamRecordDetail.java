package com.tianji.exam.domain.po;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("exam_record_detail")
@ApiModel(value="ExamRecordDetail对象", description="")
public class ExamRecordDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "记录id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "考试记录id")
    private Long examId;

    @ApiModelProperty(value = "考试人id")
    private Long userId;

    @ApiModelProperty(value = "考试记录id")
    private Long examRecordId;

    @ApiModelProperty(value = "学员答案")
    private String answer;

    @ApiModelProperty(value = "老师评语")
    private String comment;

    @ApiModelProperty(value = "是否正确")
    private Boolean correct;

    @ApiModelProperty(value = "学员得分")
    private BigDecimal score;

    @ApiModelProperty(value = "问题id")
    private Long questionId;

    @ApiModelProperty(value = "问题名称")
    private String questionName;

    @ApiModelProperty(value = "问题类型")
    private Integer questionType;

    @ApiModelProperty(value = "问题分值")
    private BigDecimal questionScore;

    @ApiModelProperty(value = "选项")
    private String options;

    @ApiModelProperty(value = "正确答案")
    private String correctAnswer;

    @ApiModelProperty(value = "答案解析")
    private String analysis;

    @ApiModelProperty(value = "难易程度")
    private Integer difficulty;


}
