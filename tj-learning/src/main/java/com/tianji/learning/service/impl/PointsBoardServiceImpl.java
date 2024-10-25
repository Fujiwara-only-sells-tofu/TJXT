package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.TableInfoContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningContstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-15
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardsList(PointsBoardQuery query) {
        //1.获取当前用户
        Long userId = UserContext.getUser();
        //2.判断是查当前赛季还是历史赛季 query.season 赛季id 为null或者为0则代表查询当前赛季
        Boolean isCurrent = query.getSeason() == null || query.getSeason() == 0;
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        //如果是查历史 需要赛季id
        Long season = query.getSeason();
        //3.查询我的排名和积分 根据 query.season 判断是查redis还是db
        PointsBoard board = isCurrent ? queryMyCurrentBoard(key) : queryMyHistoryBoard(season);
        //4.分页查询赛季列表 根据 query.season 判断是查redis还是db
        List<PointsBoard> list = isCurrent ? queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) : queryHistoryBoardList(query);
        List<Long> userIdList = list.stream().map(PointsBoard::getUserId).collect(Collectors.toList());
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIdList);
        if(CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, String> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c.getName()));
        //5.封装vo返回
        PointsBoardVO vo =new PointsBoardVO();
        vo.setRank(board.getRank());//我的排名
        vo.setPoints(board.getPoints());//我的积分
        ArrayList<PointsBoardItemVO> voList = new ArrayList<>();
        for (PointsBoard pointsBoard : list) {
            PointsBoardItemVO item = new PointsBoardItemVO();
            item.setRank(pointsBoard.getRank());
            item.setPoints(pointsBoard.getPoints());
            item.setName(userMap.get(pointsBoard.getUserId()));
            voList.add(item);
        }

        vo.setBoardList(voList);
        return vo;
    }

    /*
     * 查询历史赛季排行榜
     *
     * */
    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        // 1.计算表名
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX + query.getSeason());
        // 2.查询数据
        Page<PointsBoard> page = this.lambdaQuery().
                select(PointsBoard::getId, PointsBoard::getUserId, PointsBoard::getPoints)
                .page(query.toMpPage());

        // 3.数据处理
        List<PointsBoard> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptyList();
        }
        for (PointsBoard record : records) {
            record.setRank(record.getId().intValue());
        }
        return records;
    }

    /*
     *查询当前赛季排行榜
     * */
    public List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize) {
        //1.计算start 和 end 分页值
        int start = (pageNo - 1) * pageSize;
        int end = start + pageSize - 1;
        //2.利用zrevrange排名 会按分数倒序 分页查询
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (CollUtils.isEmpty(typedTuples)) {
            return CollUtils.emptyList();
        }
        //3.封装结果返回
        int rank = start + 1;
        List<PointsBoard> list = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取用户id
            String userId = typedTuple.getValue();//用户id
            Double score = typedTuple.getScore();//总积分值
            if (StringUtils.isBlank(userId) || score == null) {
                continue;
            }
            PointsBoard pointsBoard = new PointsBoard();
            pointsBoard.setUserId(Long.valueOf(userId));
            pointsBoard.setPoints(score.intValue());
            pointsBoard.setRank(rank++);
            list.add(pointsBoard);
        }
        return list;
    }

    /*
     * 查询我历史赛季积分
     * */
    private PointsBoard queryMyHistoryBoard(Long season) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.计算表名
        String key = POINTS_BOARD_TABLE_PREFIX + season;
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX + season);
        // 3.查询数据
        //Optional<PointsBoard> opt = lambdaQuery().eq(PointsBoard::getUserId, userId).oneOpt();
        PointsBoard one = this.lambdaQuery().
                select(PointsBoard::getId,PointsBoard::getUserId,PointsBoard::getPoints)
                .eq(PointsBoard::getUserId, userId).one();
        if (one == null) {
            return null;
        }
        // 4.转换数据

        one.setRank(one.getId().intValue());
        return one;
}

    /*
     * 查询我当前积分
     * */
    private PointsBoard queryMyCurrentBoard(String key) {
        //获取当前用户id 查询积分
        Long userId = UserContext.getUser();
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        //获取排名 从0开始 需要加一
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setRank(rank == null ? 0 : rank.intValue() + 1);
        pointsBoard.setPoints(score == null ? 0 : score.intValue());
        return pointsBoard;
    }
}
