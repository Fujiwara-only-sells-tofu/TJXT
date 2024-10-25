package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-15
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    /*
    * 获取当前和历史排行榜
    * */
    PointsBoardVO queryPointsBoardsList(PointsBoardQuery query);

    /*
     *查询当前赛季排行榜
     * */
    List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize);
}
