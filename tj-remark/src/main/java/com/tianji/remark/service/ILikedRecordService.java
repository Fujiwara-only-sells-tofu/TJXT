package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-13
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    /*
    * 点赞或者取消赞业务
    * */
    void addLikeRecord(LikeRecordFormDTO dto);

    /*
    * 批量查询点赞记录
    * */
    Set<Long> isBizLiked(List<Long> bizIds);

    /*
    * 定时任务每隔20秒将点赞总数添加到数据库
    * */
    void readLikedTimesAndSendMessage(String bizType, int maxBizSize);
}
