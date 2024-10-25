package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-11
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    /*
    * 添加评论或回复
    * */
    void saveReply(ReplyDTO replyDTO);

    /*
    * 用户端分页查询回答或者评论
    * */
    PageDTO<ReplyVO> queryReplyVoPage(ReplyPageQuery query);

    /*
    * 管理端分页查询回答或者评论
    * */
    PageDTO<ReplyVO> adminQueryReplyVoPage(ReplyPageQuery query);

    /*
    * 管理端隐藏或者显示回答或者评论
    * */
    void hiddenReply(Long id, Boolean hidden);

    /*
    * 管理端根据id查询回答或者评论详情
    * */
    ReplyVO queryReplyVoById(Long id);
}
