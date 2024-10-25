package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-09
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper recordMapper;

    /*
     * 添加用户课程
     * */
    @Override
    public void addUserLesson(Long userId, List<Long> courseIds) {
        //1.通过feign远程调用课程服务，得到课程信息
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        //2.封装po实体类，填充过期时间
        List<LearningLesson> list = new ArrayList<>();
        for (CourseSimpleInfoDTO cinfo : cinfos) {
            LearningLesson learningLesson = new LearningLesson();
            learningLesson.setUserId(userId);
            learningLesson.setCourseId(cinfo.getId());
            Integer validDuration = cinfo.getValidDuration();//课程有效期，单位是月
            if (validDuration != null && validDuration > 0) {
                learningLesson.setCreateTime(LocalDateTime.now());
                learningLesson.setExpireTime(LocalDateTime.now().plusMonths(validDuration));//过期时间=当前时间+有效期
            }
            list.add(learningLesson);
        }
        //3.批量保存
        this.saveBatch(list);
    }

    /*
     * 分页查询课程
     * */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        //1.获取当前登录人
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        //2.分页查询我的课表
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //3.远程调用课程服务，给vo中的可成名 封面 章节数 赋值
        //3.1.得到所有课程id
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //3.2.调用feign，得到课程信息
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BizIllegalException("课程信息不存在");
        }
        //4.将po中的数据，封装到vo中
        //将cinfos集合转换为map机构<课程id ， 课程对象>
        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        ArrayList<LearningLessonVO> voList = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningLessonVO vo = BeanUtils.toBean(record, LearningLessonVO.class);
            CourseSimpleInfoDTO courseSimpleInfoDTO = infoDTOMap.get(record.getCourseId());
            if (courseSimpleInfoDTO != null) {
                vo.setCourseName(courseSimpleInfoDTO.getName());
                vo.setCourseCoverUrl(courseSimpleInfoDTO.getCoverUrl());
                vo.setSections(courseSimpleInfoDTO.getSectionNum());
            }
            voList.add(vo);
        }

        //5.返回
        return PageDTO.of(page, voList);
    }


    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        //1.获取当前登录用户
        Long userId = UserContext.getUser();
        //2.查询当前用户最近学习课程 按latest_learn_time降序排序 取第一条 正在学习中的 status=1
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }
        //3.远程调用课程服务，给vo中的可成名 封面 章节数 赋值
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null) {
            throw new BizIllegalException("课程信息不存在");
        }
        //4.查询当前用户课表中的总的课程数
        Integer count = this.lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        //5.通过feign远程调用课程服务，获取小节名称和小节编号
        Long latestSectionId = lesson.getLatestSectionId();
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        //6.封装到vo返回
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(cinfo.getName());
        vo.setCourseCoverUrl(cinfo.getCoverUrl());
        vo.setSections(cinfo.getSectionNum());
        vo.setCourseAmount(count);
        CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
        vo.setLatestSectionName(cataSimpleInfoDTO.getName());//最近学习的小节名称
        vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());//最近学习的小节序号
        return vo;
    }

    @Override
    public void delUserLesson(Long userId, List<Long> courseIds) {
        //1.删除用户课程
        this.lambdaUpdate()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getCourseId, courseIds)
                .remove();
    }

    @Override
    public void deleteExpireLesson(Long courseId) {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.查询课表
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BizIllegalException("该课程不存在");
        }
        //3.判断该课程是否过期
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(expireTime)) {
            throw new BizIllegalException("该课程未过期，不能删除");
        }
        this.removeById(lesson.getId());
    }

    @Override
    public Long isLessonValid(Long courseId) {
        //1.查询当前课表是否存在该课程
        Long userId = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        //2.如果存在，判断该课程是否过期
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && now.isAfter(expireTime)) {
            return null;
        }
        return lesson.getId();
    }


    @Override
    public LearningLessonVO getLessonStatus(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        //转vo
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        return vo;
    }


    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        //根据课程id查询课表
        List<LearningLesson> list = this.lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .list();
        if (CollUtils.isNotEmpty(list)) {
            return list.size();
        }
        return 0;
    }

    @Override
    public void createLearningPlans(LearningPlanDTO learningPlanDTO) {
        //1.查询当前登录用户
        Long userId = UserContext.getUser();
        //2.查询课表
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, learningPlanDTO.getCourseId())
                .one();
        //3.修改
        this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq, learningPlanDTO.getFreq())
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }


    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery) {
        //1.查询当前用户id
        Long userId = UserContext.getUser();
        //TODO 2.查询积分

        //3.查询本周学习计划总数据 表：learning_lesson 条件 userid status in（0,1） plan_status=1

        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");//查询哪些列
        wrapper.eq("user_id", userId);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = this.getMap(wrapper);
        Integer plansTotal = 0;
        if (map != null && map.get("plansTotal") != null) {
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }

        //4.查询本周 已学习的计划数据 learning_record 条件 userid finish_time在本周区间之内 finished为true count(*)

        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);//获取本周开始时间
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);//获取本周结束时间
        Integer weekFinishedPlanNum = recordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));
        //5.查询课表数据 表：learning_lesson 条件 userid status in（0,1） plan_status=1
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            LearningPlanPageVO pageVO = new LearningPlanPageVO();
            pageVO.setTotal(0L);
            pageVO.setPages(0L);
            pageVO.setList(CollUtils.emptyList());
            return pageVO;
        }
        //6.远程调用课程服务，获取课程信息
        List<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BizIllegalException("课程不存在");
        }
        //将cinfos转为map <课程id，CourseSimpleInfoDTO>
        Map<Long, CourseSimpleInfoDTO> cinfosMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        //7.查询学习记录表 本周 当前用户下 每一门课下 已学习的小节数量
        QueryWrapper<LearningRecord> rWrapper = new QueryWrapper<>();
        //使用userId来临时存储学习的小节数量
        rWrapper.select("lesson_id as lessonId", "count(*) as userId");
        rWrapper.eq("user_id", userId);
        rWrapper.eq("finished", true);
        rWrapper.between("finish_time", weekBeginTime, weekEndTime);
        rWrapper.groupBy("lesson_id");
        List<LearningRecord> learningRecords = recordMapper.selectList(rWrapper);
        //map中key是 lessonId value是当前用户对该课程已学习的小节数量
        Map<Long, Long> courseWeekFinishNumMap = learningRecords.stream().collect(Collectors.toMap(LearningRecord::getLessonId, c -> c.getUserId()));

        //8.封装vo返回
        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekTotalPlan(plansTotal);
        vo.setWeekFinished(weekFinishedPlanNum);
        List<LearningPlanVO> voList = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningPlanVO planVO = BeanUtils.copyBean(record, LearningPlanVO.class);
            CourseSimpleInfoDTO courseSimpleInfoDTO = cinfosMap.get(record.getCourseId());
            if(courseSimpleInfoDTO != null){
                planVO.setCourseName(courseSimpleInfoDTO.getName());
                planVO.setSections(courseSimpleInfoDTO.getSectionNum());
            }
            //Long aLong = courseWeekFinishNumMap.get(record.getId());
            //if(aLong != null){
            //    planVO.setWeekLearnedSections(aLong.intValue());
            //}else {
            //
            //    planVO.setWeekLearnedSections(0);
            //}
            planVO.setWeekLearnedSections(courseWeekFinishNumMap.getOrDefault(record.getId(),0L).intValue());
            voList.add(planVO);
        }
        //vo.setList(voList);
        //vo.setPages(page.getPages());
        //vo.setTotal(page.getTotal());
        //return vo;
        return vo.pageInfo(page.getTotal(), page.getPages(), voList);
    }
}
