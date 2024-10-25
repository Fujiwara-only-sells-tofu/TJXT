package com.tianji.exam.service.impl;

import com.tianji.api.client.trade.TradeClient;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.constants.ExamType;
import com.tianji.exam.domain.dto.ExamDetailDTO;
import com.tianji.exam.domain.dto.QueryQuestionsDTO;
import com.tianji.exam.domain.dto.SubmitExamDTO;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.po.Question;
import com.tianji.exam.domain.po.QuestionBiz;
import com.tianji.exam.domain.po.QuestionDetail;
import com.tianji.exam.domain.vo.QueryQuestionsVO;
import com.tianji.exam.domain.vo.QuestionDetailVO;
import com.tianji.exam.mapper.ExamRecordMapper;
import com.tianji.exam.service.IExamRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.exam.service.IQuestionBizService;
import com.tianji.exam.service.IQuestionDetailService;
import com.tianji.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-25
 */
@Service
@RequiredArgsConstructor
public class ExamRecordServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements IExamRecordService {


    private final TradeClient tradeClient;


    private final IQuestionBizService bizService;

    private final IQuestionService questionService;

    private final IQuestionDetailService questionDetailService;


    @Override
    public QueryQuestionsVO queryQuestions(QueryQuestionsDTO dto) {
        //0.校验
        Long courseId = dto.getCourseId();
        Long sectionId = dto.getSectionId();
        if(courseId == null){
            throw new BadRequestException("课程id不能为空");
        }

        //1.查询课程id 看是否购买了课程
        Long userId = UserContext.getUser();
        //1.1远程调用交易服务查询用户是否购买了该课程
        Boolean isBuy = tradeClient.checkMyLesson(courseId);
        if(!isBuy){
            throw new BizIllegalException("您还没有购买此课程，请先购买后再进行考试！");
        }
        //2.查询考试记录 条件是用户id 课程id 小节id 考试类型 重复的话直接结束 没有的话查询考试题目
        List<ExamRecord> list = this.lambdaQuery()
                .eq(ExamRecord::getUserId, userId)
                .eq(courseId != null, ExamRecord::getCourseId, courseId)
                .eq(sectionId != null, ExamRecord::getSectionId, sectionId)
                .eq(ExamRecord::getType, dto.getType())
                .list();
        if(dto.getType() == ExamType.EXAM && CollUtils.isNotEmpty(list)){
            throw new BizIllegalException("您已经考试过了，请不要重复考试");
        }

        //2.1 查询考试题目
        List<QuestionBiz> bizList = bizService.lambdaQuery()
                .eq(sectionId != null, QuestionBiz::getBizId, sectionId)
                .list();
        if(CollUtils.isEmpty(bizList)){
            throw new BizIllegalException("该课程或小节还没有考试题目，请联系管理员");
        }
        Set<Long> questionIds = bizList.stream().map(QuestionBiz::getQuestionId).collect(Collectors.toSet());
        List<Question> questions = questionService.listByIds(questionIds);

        //3.新增考试记录
        ExamRecord examRecord = new ExamRecord();
        examRecord.setUserId(userId);
        examRecord.setType(dto.getType());
        examRecord.setCourseId(courseId.toString());
        examRecord.setSectionId(sectionId.toString());
        examRecord.setIsCommited(false);
        this.save(examRecord);

        //4.返回考试题目
        QueryQuestionsVO vo = new QueryQuestionsVO();
        List<QuestionDetailVO> questionDetailVOS = BeanUtils.copyList(questions, QuestionDetailVO.class);
        //设置选项
        for (QuestionDetailVO questionDetailVO : questionDetailVOS) {
            QuestionDetail questionDetail = questionDetailService.getById(questionDetailVO.getId());

            questionDetailVO.setOptions(questionDetail.getOptions());
        }
        vo.setQuestions(questionDetailVOS);
        vo.setId(examRecord.getId());
        return vo;
    }


    //@Override
    //public void submitExamResult(SubmitExamDTO dto) {
    //    //1.获取用户id 考试记录id
    //    Long userId = UserContext.getUser();
    //    Long id = dto.getId();
    //    //2.查询考试记录
    //    ExamRecord examRecord = this.getById(id);
    //    if(examRecord == null){
    //        throw new BizIllegalException("考试记录不存在");
    //    }
    //    if(!examRecord.getUserId().equals(userId)){
    //        throw new BizIllegalException("您没有权限提交该考试");
    //    }
    //    if(examRecord.getType() == ExamType.EXAM && examRecord.getIsCommited()){
    //        throw new BizIllegalException("您已经提交过了，请不要重复提交");
    //    }
    //    //3.计算分数
    //    List<ExamDetailDTO> examDetails = dto.getExamDetails();
    //    Map<String, String> resultMap = examDetails.stream()
    //            .collect(Collectors.toMap(
    //                    ExamDetailDTO::getQuestionId, // 键：问题 ID
    //                    ExamDetailDTO::getAnswer // 值：题目难易程度
    //            ));
    //}
}
