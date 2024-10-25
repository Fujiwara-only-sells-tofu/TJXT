package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-09
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    /*
    添加用户课程到
    * */
    void addUserLesson(Long userId, List<Long> courseIds);

    /*
    分页查询用户课程
    * */
    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    /*
    * 查询用户正在学习的课程
    * */
    LearningLessonVO queryMyCurrentLesson();

    /*
    * 检查课程是否有效
    * */
    Long isLessonValid(Long courseId);

    /*
    * 删除课程
    * */
    void delUserLesson(Long userId, List<Long> courseIds);

    /*
    * 删除过期的课程
    * */
    void deleteExpireLesson(Long courseId);

    /*
    * 查询课程状态
    * */
    LearningLessonVO getLessonStatus(Long courseId);

    /*
    * 查询课程学习人数
    * */
    Integer countLearningLessonByCourse(Long courseId);

    /*
    * 创建学习计划
    * */
    void createLearningPlans(LearningPlanDTO learningPlanDTO);

    /*
    * 查询学习计划
    * */
    LearningPlanPageVO queryMyPlans(PageQuery pageQuery);
}
