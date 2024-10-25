package com.tianji.exam.service;

import com.tianji.exam.domain.dto.QueryQuestionsDTO;
import com.tianji.exam.domain.dto.SubmitExamDTO;
import com.tianji.exam.domain.po.ExamRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.exam.domain.vo.QueryQuestionsVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-25
 */
public interface IExamRecordService extends IService<ExamRecord> {

    /*
    * 用户查询题目并开始考试
    * */
    QueryQuestionsVO queryQuestions(QueryQuestionsDTO dto);

    ///*
    //* 提交考试结果
    //* */
    //void submitExamResult(SubmitExamDTO dto);
}
