package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-13
 */
@Api(tags = "点赞相关接口")
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Slf4j
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;

    @ApiOperation("点赞或取消赞")
    @PostMapping
    public void addLikeRecord(@RequestBody @Validated LikeRecordFormDTO dto){
        likedRecordService.addLikeRecord(dto);
    }


    @GetMapping("list")
    @ApiOperation("查询指定业务id的点赞状态")
    public Set<Long> isBizLiked(@RequestParam("bizIds") List<Long> bizIds){
        return likedRecordService.isBizLiked(bizIds);
    }

}
