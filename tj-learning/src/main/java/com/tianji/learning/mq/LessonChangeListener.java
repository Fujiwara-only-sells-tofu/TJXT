package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService lessonService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void onMsg(OrderBasicDTO dto) {
        log.info("LessonChangeListener 接收到了消息，用户{}，添加课程{}", dto.getUserId(), dto.getCourseIds());
        //1. 校验
        if (dto.getUserId() == null
                || dto.getOrderId() == null
                || CollUtils.isEmpty(dto.getCourseIds())) {
            return;
        }
        //2. 调用service添加课程
        lessonService.addUserLesson(dto.getUserId(), dto.getCourseIds());
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.refund.queue"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void offMsg(OrderBasicDTO dto) {
        log.info("LessonChangeListener 接收到了消息，用户{}，删除课程{}", dto.getUserId(), dto.getCourseIds());
        //1. 校验
        if (dto.getUserId() == null
                || dto.getOrderId() == null
                || CollUtils.isEmpty(dto.getCourseIds())) {
            return;
        }
        //2. 调用service添加课程
        lessonService.delUserLesson(dto.getUserId(), dto.getCourseIds());
    }
}
