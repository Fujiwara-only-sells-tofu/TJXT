package com.tianji.exam.domain.po;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import com.tianji.exam.constants.ExamType;
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
@TableName("exam_record")
@ApiModel(value="ExamRecord对象", description="")
public class ExamRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "考试记录id")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @ApiModelProperty(value = "考试人id")
    private Long userId;

    @ApiModelProperty(value = "考试类型（考试、练习）")
    private ExamType type;

    @ApiModelProperty(value = "得分")
    private BigDecimal score;

    @ApiModelProperty(value = "提交时间")
    private LocalDateTime commitTime;

    @ApiModelProperty(value = "考试用时，单位秒")
    private Integer duration;

    @ApiModelProperty(value = "课程id")
    private String courseId;

    @ApiModelProperty(value = "小节id")
    private String sectionId;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "是否提交过")
    private Boolean isCommited;
}
