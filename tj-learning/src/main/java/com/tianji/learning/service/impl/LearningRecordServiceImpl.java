package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-10
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private  final ILearningLessonService learningLessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler taskHandler;

    private final RabbitMqHelper mqHelper;
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //2.查询课表信息 条件 userId courseId
        LearningLesson lesson = learningLessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson == null){
            throw new RuntimeException("该课程未加入到课表中");
        }
        //3.查询课程学习记录 条件 lessonId userid
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .eq(LearningRecord::getUserId, userId)
                .list();
        //4.封装为LearningLessonDTO返回
        List<LearningRecordDTO> dtoList = BeanUtils.copyList(recordList, LearningRecordDTO.class);
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(lesson.getId());
        learningLessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        learningLessonDTO.setRecords(dtoList);
        return learningLessonDTO;
    }


    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.处理学习记录
        boolean isFinshed; // 代表是否第一次学完
        if(dto.getSectionType().equals(SectionType.VIDEO)){
            //2.1提交视频播放记录
            isFinshed=handleVideoRecord(userId,dto);
        }else {
            //2.2提交考试记录
            isFinshed=handleExamRecord(userId,dto);
        }

        //3.处理课表数据
        if(isFinshed){//如果本小节 不是第一次学完，不用处理课表数据
            return;
        }
        //发送mq增加学习积分
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.LEARN_SECTION,
                SignInMessage.of(userId, 10));// 签到积分是基本得分+奖励积分
        handleLessonData(dto);
    }

    //处理课表数据
    private void handleLessonData(LearningRecordFormDTO dto) {
        //1.先获取课表
        LearningLesson lesson = learningLessonService.getById(dto.getLessonId());
        if(lesson == null){
            throw new BizIllegalException("课表不存在");
        }
        //2.判断是否第一次学完
        Boolean allFinished = false;

        //3.远程调用feign 得到课程信息
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if(cinfo == null){
            throw new BizIllegalException("课程不存在");
        }
        //4.如果本小节是第一次学完，判断课程是都全部学完
        Integer sectionNum = cinfo.getSectionNum();
        Integer learnedSections = lesson.getLearnedSections();
        allFinished = learnedSections + 1 >= sectionNum;

        //5.更新课表数据
        learningLessonService.lambdaUpdate()
                .set(allFinished,LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(lesson.getLearnedSections()==0,LearningLesson::getStatus,LessonStatus.LEARNING)
                .set(LearningLesson::getLatestLearnTime,dto.getCommitTime())
                .set(LearningLesson::getLatestSectionId,dto.getSectionId())
                //.set(LearningLesson::getLearnedSections,lesson.getLearnedSections()+1)
                .setSql(allFinished,"learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId,lesson.getId())
                .update();
    }

    //处理视频数据
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        //1.判断是否存在学习记录
        LearningRecord learningRecord = queryOldRecord(dto.getLessonId(),dto.getSectionId());
        //LearningRecord learningRecord = this.lambdaQuery()
        //        .eq(LearningRecord::getLessonId, dto.getLessonId())
        //        .eq(LearningRecord::getSectionId, dto.getSectionId())
        //        .one();
        if(learningRecord == null){
            //不存在学习记录，新增
            //转po
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);
            //保存
            boolean result = this.save(record);
            if(!result){
                throw new DbException("新增视频记录失败");
            }
            return false;//代表本小节没有学完
        }
        //存在学习记录，更新
        //判断本小节是否为第一次学完
        Boolean isFinished= !learningRecord.getFinished() && dto.getMoment()*2>=dto.getDuration();
        if(!isFinished){
            LearningRecord record = new LearningRecord();
            record.setLessonId(dto.getLessonId());
            record.setSectionId(dto.getSectionId());
            record.setMoment(dto.getMoment());
            record.setFinished(learningRecord.getFinished());
            record.setId(learningRecord.getId());
            taskHandler.addLearningRecordTask(record);
            return false;//代表本小节没有学完
        }
        boolean result = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(isFinished, LearningRecord::getFinished, true)
                .set(isFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId())
                .update();
        if(!result){
            throw new DbException("更新视频记录失败");
        }
        taskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());
        return isFinished;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        //1.查询缓存
        LearningRecord cache = taskHandler.readRecordCache(lessonId, sectionId);
        //2.如果命中直接返回
        if(cache!=null){
            return cache;
        }
        //3.如果没有命中，查询数据库
        LearningRecord dbRecord = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        if(dbRecord == null){
            return  null;
        }
        //4.存入缓存
        taskHandler.writeRecordCache(dbRecord);
        return dbRecord;
    }

    //处理考试数据
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        //转po
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        //封装po对象
        record.setUserId(userId);
        record.setFinished(true);// 提交考试记录，代表本小节已经学完
        record.setFinishTime(dto.getCommitTime());
        //保存
        boolean result = this.save(record);
        if(!result){
            throw new DbException("新增考试记录失败");
        }
        return  true;//因为是考试，所以默认已经学完

    }
}
