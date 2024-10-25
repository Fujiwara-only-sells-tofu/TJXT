package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-09
 */
@Slf4j
@RestController
@RequestMapping("/lessons")
@Api(tags = "学生课程有关接口")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService learningLessonService;

    @ApiOperation("查询我的课表，排序字段 latest_learn_time:学习时间排序，create_time:购买时间排序")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery){
        return learningLessonService.queryMyLessons(pageQuery);
    }


    @ApiOperation("查询我正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLesson(){
        return learningLessonService.queryMyCurrentLesson();
    }

    @ApiOperation("删除过期的课程")
    @DeleteMapping("/{courseId}")
    public void deleteExpireLesson(@PathVariable Long courseId){
        learningLessonService.deleteExpireLesson(courseId);
    }

    @ApiOperation("检查课程是否有效")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId){
        return learningLessonService.isLessonValid(courseId);

    }

    @ApiOperation("查询用户课表中指定课程状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO getLessonStatus(@PathVariable("courseId") Long courseId){
        return learningLessonService.getLessonStatus(courseId);
    }


    @ApiOperation("统计课程的学习人数")
    @GetMapping("/{courseId}/count")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return learningLessonService.countLearningLessonByCourse(courseId);
    }

    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@RequestBody @Validated  LearningPlanDTO learningPlanDTO){
        learningLessonService.createLearningPlans(learningPlanDTO);
    }

    @ApiOperation("查询我的学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery){
        return learningLessonService.queryMyPlans(pageQuery);
    }

}
