package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-15
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    /*
     * 创建赛季表
     *
     * */
    void createPointsBoardTableBySeason(Integer seasonId);
}
