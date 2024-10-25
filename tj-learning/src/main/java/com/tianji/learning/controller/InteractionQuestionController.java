package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-11
 */
@Api(tags = "互动问题相关接口-用户端")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
@Slf4j
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;

    @PostMapping
    @ApiOperation("新增互动问题")
    public void saveQuestion(@RequestBody @Validated QuestionFormDTO dto){
        questionService.saveQuestion(dto);
    }

    @ApiOperation("修改互动问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable Long id,
                               @RequestBody  QuestionFormDTO dto){
        questionService.updateQuestion(id,dto);
    }

    @ApiOperation("用户端分页查询互动问题")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query){
        return questionService.queryQuestionPage(query);
    }

    @ApiOperation("用户端根据问题ID查看详情")
    @GetMapping("/{id}")
    public QuestionVO queryQuestionById(@PathVariable Long id) {
        return questionService.queryQuestionById(id);
    }

    @ApiOperation("根据问题id删除互动问题")
    @DeleteMapping("/{id}")
    public void deleteQuestionById(@PathVariable Long id) {
        questionService.deleteQuestionById(id);

    }
}
