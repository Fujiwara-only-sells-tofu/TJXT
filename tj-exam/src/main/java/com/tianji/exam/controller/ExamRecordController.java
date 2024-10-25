package com.tianji.exam.controller;


import com.tianji.exam.domain.dto.QueryQuestionsDTO;
import com.tianji.exam.domain.dto.SubmitExamDTO;
import com.tianji.exam.domain.vo.QueryQuestionsVO;
import com.tianji.exam.service.IExamRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-25
 */
@RestController
@RequestMapping("/exams")
@RequiredArgsConstructor
@Api(tags = "考试记录管理接口")
@Slf4j
public class ExamRecordController {

    private final IExamRecordService examRecordService;

    @PostMapping
    @ApiOperation("用户查询考题信息，并开始考试")
    public QueryQuestionsVO queryQuestions(@RequestBody QueryQuestionsDTO dto) {
       return examRecordService.queryQuestions(dto);
    }

    //
    //@PostMapping("/details")
    //@ApiOperation("提交考试结果")
    //public void submitExamResult(@RequestBody SubmitExamDTO dto) {
    //    examRecordService.submitExamResult(dto);
    //}

}
