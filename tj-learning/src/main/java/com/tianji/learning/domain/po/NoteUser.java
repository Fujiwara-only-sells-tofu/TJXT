package com.tianji.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 采用笔记表
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("note_user")
@ApiModel(value="NoteUser对象", description="采用笔记表")
public class NoteUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "引用的笔记id")
    private Long noteId;

    @ApiModelProperty(value = "引用者id")
    private Long userId;

    @ApiModelProperty(value = "是否是采集他人的笔记，默认false")
    private Boolean isGathered;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;


}
