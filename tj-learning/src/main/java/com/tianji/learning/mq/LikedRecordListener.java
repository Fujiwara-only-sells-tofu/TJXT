package com.tianji.learning.mq;

import com.tianji.api.msg.LikedTimesDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikedRecordListener {

    private final IInteractionReplyService replyService;

    /*
    * QA问答系统 消费者
    * */
//    @RabbitListener(bindings = @QueueBinding(
//            value=@Queue(name = "qa.liked.times.queue"),
//            exchange = @Exchange(name = MqConstants.Exchange.LIKE_RECORD_EXCHANGE,type = ExchangeTypes.TOPIC),
//            key =MqConstants.Key.QA_LIKED_TIMES_KEY
//    ))
//    public void listenReplyLikedTimesChange(LikedTimesDTO dto){
//        log.info("listenReplyLikedTimesChange 收到点赞数变更消息:{}",dto);
//        InteractionReply reply = replyService.getById(dto.getBizId());
//        if(reply == null){
//            return;//这个地方不能抛异常，如果抛异常mq会重试
//        }
//        reply.setLikedTimes(dto.getLikedTimes());
//        replyService.updateById(reply);
//    }


    @RabbitListener(bindings = @QueueBinding(
            value=@Queue(name = "qa.liked.times.queue"),
            exchange = @Exchange(name = MqConstants.Exchange.LIKE_RECORD_EXCHANGE,type = ExchangeTypes.TOPIC),
            key =MqConstants.Key.QA_LIKED_TIMES_KEY
    ))
    public void listenReplyLikedTimesChange(List<LikedTimesDTO> list){
        log.info("listenReplyLikedTimesChange 收到点赞数变更消息:{}",list);
        //消息转po
        ArrayList<InteractionReply> replyList = new ArrayList<>();
        for (LikedTimesDTO dto : list) {
            InteractionReply reply = new InteractionReply();
            reply.setLikedTimes(dto.getLikedTimes());
            reply.setId(dto.getBizId());//评论id
            replyList.add(reply);
        }
        replyService.updateBatchById(replyList);
    }

}
