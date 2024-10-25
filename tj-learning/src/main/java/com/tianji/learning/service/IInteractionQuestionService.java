package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-11
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    /*
     * 新增问题
     * */
    void saveQuestion(QuestionFormDTO dto);

    /*
     * 修改问题
     * */
    void updateQuestion(Long id, QuestionFormDTO dto);

    /*
     *用户端分页查询问题
     * */
    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    /*
    * 用户端根据id查询问题详情
    * */
    QuestionVO queryQuestionById(Long id);

    /*
    * 管理端分页查询问题
    * */
    PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query);

    /*
    根据问题id删除问题
    * */
    void deleteQuestionById(Long id);

    /*
    * 管理端设置问题是否可见
    * */
    void hiddenQuestion(Long id, Boolean hidden);

    /*
    * 管理端根据问题id查询问题详情
    * */
    QuestionAdminVO queryQuestion(Long id);
}
