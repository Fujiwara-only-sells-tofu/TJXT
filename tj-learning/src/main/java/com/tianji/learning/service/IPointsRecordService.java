package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.msg.SignInMessage;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-15
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    /*
    * 增加积分
    * */
    void addPointsRecord(SignInMessage message, PointsRecordType type);

    /*
    * 查询我今日的积分情况
    * */
    List<PointsStatisticsVO> queryMyTodayPoints();

    /*
    * 创建上个赛季积分明细表
    * */
    void createPointsRecordTableBySeason(Integer seasonId);
}
