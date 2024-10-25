package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-11
 */
@Api(tags = "回答或评论的相关接口-管理端")
@RestController
@RequestMapping("/admin/replies")
@RequiredArgsConstructor
@Slf4j
public class InteractionReplyAdminController {
    private final IInteractionReplyService replyService;

    @ApiOperation("管理端分页查询回答或评论")
    @GetMapping("/page")
    public PageDTO<ReplyVO> adminQueryReplyVoPage(ReplyPageQuery query) {
        return replyService.adminQueryReplyVoPage(query);
    }

    @ApiOperation("管理端隐藏回答或者评论")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenReply(
            @ApiParam(value = "问题id", example = "1") @PathVariable("id") Long id,
            @ApiParam(value = "是否隐藏，true/false", example = "true") @PathVariable("hidden") Boolean hidden
    ){
        replyService.hiddenReply(id, hidden);
    }


    @ApiOperation("管理端根据回答或者评论id查看详情")
    @GetMapping("/{id}")
    public ReplyVO queryReplyById(@PathVariable Long id) {
       return replyService.queryReplyVoById(id);
    }
}
