package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private static final List<String> BIZ_TYPES = List.of("QA", "NOTE");
    private static final int MAX_BIZ_SIZE = 30;
    private final ILikedRecordService recordService;
    //每20秒执行一次 将redis中 业务类型下 某业务的点赞总数 发消息到mq然后保存到数据库
    //@Scheduled(cron = "0/20 * *  * * ? ")
    //@Scheduled(fixedDelay = 20000)
    @XxlJob("timingSendToMq")
    public void checkLikedTimes(){
        for (String bizType : BIZ_TYPES) {
            recordService.readLikedTimesAndSendMessage(bizType,MAX_BIZ_SIZE);
        }

    }
}
