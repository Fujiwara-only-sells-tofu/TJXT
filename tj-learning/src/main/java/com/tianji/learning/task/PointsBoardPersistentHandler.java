package com.tianji.learning.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningContstants.POINTS_BOARD_TABLE_PREFIX;
import static com.tianji.learning.constants.LearningContstants.POINTS_RECORD_KEY_PREFIX;


@Component
@RequiredArgsConstructor
@Slf4j
public class PointsBoardPersistentHandler {


    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate redisTemplate;

    private final IPointsRecordService recordService;

    /*
     * 创建上赛季榜单表
     * */
    //@Scheduled(cron = "0 0 3 1 * ?") // 每月1号，凌晨3点执行
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        //1.获取上个月当前时间点
        LocalDate time = LocalDate.now().minusMonths(1);
        //2.查询赛季表获取赛季id
        PointsBoardSeason season = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)//开始时间小于等于当前时间的上一个月
                .ge(PointsBoardSeason::getEndTime,time)//结束时间大于等于当前时间的上一个月
                .one();
        log.debug("上赛季信息 ：{}",season);
        if(season == null){
            return;
        }
        Integer seasonId = season.getId();
        //3.创建上赛季榜单表
        seasonService.createPointsBoardTableBySeason(seasonId);

    }

    @XxlJob("createPointRecordTableJob")
    public void createPointsRecordTableOfLastSeason() {
        //1.获取上个月当前时间点
        LocalDate time = LocalDate.now().minusMonths(1);
        //2.查询赛季表获取赛季id
        PointsBoardSeason season = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)//开始时间小于等于当前时间的上一个月
                .ge(PointsBoardSeason::getEndTime,time)//结束时间大于等于当前时间的上一个月
                .one();
        log.debug("上赛季信息 ：{}",season);
        if(season == null){
            return;
        }
        Integer seasonId = season.getId();

        //4.创建上赛季积分明细表
        recordService.createPointsRecordTableBySeason(seasonId);
    }

    /*
     * 持久化上赛季（上个月的）积分明细数据 到db中
     * */
    @XxlJob("savePointsRecord2DB")//任务名字要和 xxljob控制台 任务的jobhandle值保持一致
    public void savePointsRecord2DB(){
        //1.获取当前时间点
        LocalDate time = LocalDate.now();
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        PointsBoardSeason season = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, lastMonth)//开始时间小于等于当前时间的上一个月
                .ge(PointsBoardSeason::getEndTime,lastMonth)//结束时间大于等于当前时间的上一个月
                .one();
        log.debug("上赛季信息 ：{}",season);
        if(season == null){
            return;
        }

        //2.查询赛季表points_board_season 获取上赛季所有积分明细信息
        List<PointsRecord> list = recordService.lambdaQuery()
                .lt(PointsRecord::getCreateTime, time)//创建时间小于等于当前时间的
                .list();
        log.debug("上赛季积分明细信息 ：{}",list);
        if(CollUtils.isEmpty(list)){
            return;
        }
        //3.计算动态表名 并存入threadlocal
        String tableName=POINTS_RECORD_KEY_PREFIX + season.getId();
        log.debug("上赛季积分明细的动态表名 ：{}",tableName);
        TableInfoContext.setInfo(tableName);
        recordService.saveBatch(list);
        //4.清空threadlocal中数据
        TableInfoContext.remove();
        List<Long> ids = list.stream().map(PointsRecord::getId).collect(Collectors.toList());
        recordService.removeByIds(ids);

    }
    /*
    * 持久化上赛季（上个月的）榜单数据 到db中
    * */
    @XxlJob("savePointsBoard2DB")//任务名字要和 xxljob控制台 任务的jobhandle值保持一致
    public void savePointsBoard2DB(){
        //1.获取上个月 当前时间点
        LocalDate time = LocalDate.now().minusMonths(1);
        //2.查询赛季表points_board_season 获取上赛季信息
        PointsBoardSeason season = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)//开始时间小于等于当前时间的上一个月
                .ge(PointsBoardSeason::getEndTime,time)//结束时间大于等于当前时间的上一个月
                .one();
        log.debug("上赛季信息 ：{}",season);
        if(season == null){
            return;
        }
        //3.计算动态表名 并存入threadlocal
        String tableName=POINTS_BOARD_TABLE_PREFIX + season.getId();
        log.debug("上赛季的动态表名 ：{}",tableName);
        TableInfoContext.setInfo(tableName);
        //4.分页获取redis上赛季积分排行榜数据
        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;

        int shardIndex = XxlJobHelper.getShardIndex();//当前分片的索引 从0开始
        int shardTotal = XxlJobHelper.getShardTotal();//总分片数

        int pageNo= shardIndex+1;
        int pageSize = 5;//应为1000 但是由于数据较少，改为5作为测试
        while (true) {
            log.debug("处理第 {} 页数据",pageNo);
            List<PointsBoard> pointsBoards = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if(CollUtils.isEmpty(pointsBoards)){
                break;
            }
            pageNo+=shardTotal;
            //5.持久化到db相应的赛季表中 批量新增
            pointsBoards.forEach( pointsBoard -> {
                    pointsBoard.setId(pointsBoard.getRank().longValue());
                    pointsBoard.setRank(null);
                    }
            );
            pointsBoardService.saveBatch(pointsBoards);
        }
        //6.清空threadlocal中数据
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        // 3.删除
        redisTemplate.unlink(key);//unlink适用于删除大量的数据，立刻释放分配的空间

    }
}
