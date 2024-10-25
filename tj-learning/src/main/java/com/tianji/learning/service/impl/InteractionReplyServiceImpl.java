package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.Constant;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-11
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;

    private final UserClient userClient;

    private final RemarkClient remarkClient;
    private final RabbitMqHelper mqHelper;

    @Override
    public void saveReply(ReplyDTO replyDTO) {
        //1.获取当前登录用户
        Long userId = UserContext.getUser();
        //2.保存回答或者评论 interaction_reply
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        this.save(reply);
        //3.判断是否是回答 anwerId 为空是回答
        //获取问题实体
        InteractionQuestion question = questionMapper.selectById(replyDTO.getQuestionId());
        if(replyDTO.getAnswerId() != null){
            //4.如果不是回答 累加回答下的评论数
            InteractionReply answerInfo = getById(replyDTO.getAnswerId());
            answerInfo.setReplyTimes(answerInfo.getReplyTimes() + 1);
            //发送mq消息增加评论积分
            mqHelper.send(
                    MqConstants.Exchange.LEARNING_EXCHANGE,
                    MqConstants.Key.WRITE_REPLY,
                    SignInMessage.of(userId, 10));

            this.updateById(answerInfo);
        }else{
            //5.如果是回答 修改问题表最近一次回答Id 同时累加问题表回答次数
            question.setLatestAnswerId(reply.getId());
            question.setAnswerTimes(question.getAnswerTimes() + 1);
            //发送mq消息增加回答积分
            mqHelper.send(
                    MqConstants.Exchange.LEARNING_EXCHANGE,
                    MqConstants.Key.WRITE_REPLY,
                    SignInMessage.of(userId, 5));
        }
        //6.判断是否是学生提交 isStudent true为学生提交，如果是则将问题表改问题的status改为未查看
        if(replyDTO.getIsStudent()){
            question.setStatus(QuestionStatus.UN_CHECK);
        }

        questionMapper.updateById(question);
    }


    @Override
    public PageDTO<ReplyVO> queryReplyVoPage(ReplyPageQuery query) {
        //1.校验参数 questionId和answerId不能都为空
        if(query.getQuestionId() == null && query.getAnswerId() == null){
            throw new BadRequestException("用户id和回答id不能都为空");
        }
        //2.分页查询评论表
        Page<InteractionReply> page = this.lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                //.eq(query.getAnswerId() != null, InteractionReply::getAnswerId,query.getAnswerId())
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0 : query.getAnswerId())
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPage(
                        //先按照点赞数量排序，再按照创建时间排序
                        new OrderItem(Constant.DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(Constant.DATA_FIELD_NAME_CREATE_TIME, true)));
        List<InteractionReply> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(0L,0L);
        }
        
        //3.封装其他数据
        HashSet<Long> uids = new HashSet<>();
        HashSet<Long> targetReplyIds = new HashSet<>();
        List<Long> answerIds = new ArrayList<>();
        for (InteractionReply record : records) {
            if(!record.getAnonymity()){ // 如果不是匿名。将所有的用户id添加到集合中
                uids.add(record.getUserId());
                uids.add(record.getTargetUserId());
            }
            if(record.getTargetReplyId() != null && record.getTargetReplyId() > 0){//收集回复评论的id
                targetReplyIds.add(record.getTargetReplyId());
            }
            answerIds.add(record.getId());
        }
        //查询目标回复，如果目标回复不是匿名，则需要查询出目标回复的用户信息
        if(targetReplyIds.size() > 0){
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            uids.addAll(targetUserIds);

        }
        List<UserDTO> userDTOList = userClient.queryUserByIds(uids);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if(userDTOList!=null){
            userDTOMap = userDTOList.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        }
        //查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(answerIds);
        //4.封装vo返回
        List<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            if(!record.getAnonymity()){
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if(userDTO != null){
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserType(userDTO.getType());
                }
            }
            UserDTO targetUserDTO = userDTOMap.get(record.getTargetUserId());
            if(targetUserDTO != null){
                vo.setTargetUserName(targetUserDTO.getName());
            }
            //if(bizLiked == null){
            //    vo.setLiked(false);
            //
            //}else{
            //    vo.setLiked(bizLiked.contains(record.getId()));
            //}
            vo.setLiked(bizLiked.contains(record.getId()));
            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }


    @Override
    public PageDTO<ReplyVO> adminQueryReplyVoPage(ReplyPageQuery query) {
        //1.校验参数
        if(query.getQuestionId() == null && query.getAnswerId() == null){
            throw new BadRequestException("用户id和回答id不能都为空");
        }
        //2.分页查询数据
        Page<InteractionReply> page = this.lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0 : query.getAnswerId())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionReply> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(0L,0L);
        }
        //收集用户id
        HashSet<Long> uids = new HashSet<>();
        List<Long> answerIds = new ArrayList<>();
        for (InteractionReply record : records) {
                uids.add(record.getUserId());
                uids.add(record.getTargetUserId());
                answerIds.add(record.getId());
        }
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if(userDTOS!=null){
            userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        }
        //查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(answerIds);
        //封装数据
        ArrayList<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            UserDTO userDTO = userDTOMap.get(record.getUserId());
            if(userDTO != null){
                vo.setUserName(userDTO.getName());//回复者名字
                vo.setUserIcon(userDTO.getIcon());//回复者头像
                vo.setUserType(userDTO.getType());//回复者类型 学生 员工 老师
            }
            UserDTO targetUser = userDTOMap.get(record.getTargetUserId());
            vo.setTargetUserName(targetUser.getName());//目标用户名字
            if(bizLiked == null){
                vo.setLiked(false);
            }else{
                vo.setLiked(bizLiked.contains(record.getId()));
            }
            voList.add(vo);
        }
        return PageDTO.of(page,voList);
    }


    @Override
    @Transactional
    public void hiddenReply(Long id, Boolean hidden) {
        //校验参数
        if(id == null || hidden == null){
            throw new BadRequestException("参数错误");
        }
        // 1.查询
        InteractionReply old = getById(id);
        if (old == null) {
            return;
        }

        // 2.隐藏回答
        InteractionReply reply = new InteractionReply();
        reply.setId(id);
        reply.setHidden(hidden);
        updateById(reply);

        // 3.隐藏评论，先判断是否是回答，回答才需要隐藏下属评论
        if (old.getAnswerId() != null && old.getAnswerId() != 0) {
            // 3.1.有answerId，说明自己是评论，无需处理
            return;
        }
        // 3.2.没有answerId，说明自己是回答，需要隐藏回答下的评论
        lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getAnswerId, id)
                .update();
    }


    @Override
    public ReplyVO queryReplyVoById(Long id) {
        //1.校验参数
        if(id == null){
            throw new BadRequestException("参数错误");
        }
        //2.查询
        InteractionReply reply = this.getById(id);
        if(reply == null){
            return null;
        }
        ReplyVO vo = BeanUtils.copyBean(reply, ReplyVO.class);
        Long userId = reply.getUserId();
        UserDTO userDTO = userClient.queryUserById(userId);
        //3.封装数据 加入用户头像
        vo.setUserIcon(userDTO.getIcon());
        vo.setUserName(userDTO.getName());
        return vo;
    }
}
