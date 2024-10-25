package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-11
 */
@Api(tags = "互动问题相关接口-管理端")
@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
@Slf4j
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("管理端分页查询互动问题")
    @GetMapping("/page")
    public PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query){
        return questionService.queryQuestionAdminVOPage(query);
    }

    @ApiOperation("管理端隐藏或显示互动问题")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenQuestion(@PathVariable Long id,@PathVariable Boolean hidden){
        questionService.hiddenQuestion(id, hidden);
    }


    @ApiOperation("管理端查看问题详情")
    @GetMapping("/{id}")
    public QuestionAdminVO queryQuestion(@PathVariable Long id){
        return questionService.queryQuestion(id);
    }
}
