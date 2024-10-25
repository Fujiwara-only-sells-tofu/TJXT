package com.tianji.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 学习笔记表
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("note")
@ApiModel(value="Note对象", description="学习笔记表")
public class Note implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "笔记id")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @ApiModelProperty(value = "用户id")
    private Long userId;

    @ApiModelProperty(value = "课程id")
    private Long courseId;

    @ApiModelProperty(value = "章id")
    private Long chapterId;

    @ApiModelProperty(value = "小节id")
    private Long sectionId;

    @ApiModelProperty(value = "记录笔记时的视频播放时间，单位秒")
    private Integer noteMoment;

    @ApiModelProperty(value = "笔记内容")
    private String content;

    @ApiModelProperty(value = "是否是隐私笔记，默认false")
    private Boolean isPrivate;

    @ApiModelProperty(value = "是否被折叠（隐藏）默认false")
    private Boolean hidden;

    @ApiModelProperty(value = "被隐藏的原因")
    private String hiddenReason;

    @ApiModelProperty(value = "笔记作者id")
    private Long authorId;

    @ApiModelProperty(value = "被采集笔记的id")
    private Long gatheredNoteId;

    @ApiModelProperty(value = "是否是采集他人的笔记，默认false")
    private Boolean isGathered;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;


}
