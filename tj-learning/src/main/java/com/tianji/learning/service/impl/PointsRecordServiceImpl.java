package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tianji.learning.constants.LearningContstants.POINTS_RECORD_KEY_PREFIX;


/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-15
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addPointsRecord(SignInMessage message, PointsRecordType type) {
        //1.校验
        if (message.getUserId() == null || message.getPoints() == null) {
            return;
        }
        //用户实际得到的分数
        int realPoint = message.getPoints();
        //2.判断该积分类型是否有上限 type.maxPoints>0
        if (type.getMaxPoints() > 0) {
            //有上限，判断该用户 该积分类型 今日以得积分 points_record 条件 userId type 今天 sum（points）

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
            LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("sum(points) as totalPoints");
            wrapper.eq("user_id", message.getUserId());
            wrapper.eq("type", type);
            wrapper.between("create_time", dayStartTime, dayEndTime);
            Map<String, Object> map = this.getMap(wrapper);
            int currentTypePoints = 0;
            if (map != null) {
                BigDecimal totalPoints = (BigDecimal) map.get("totalPoints");
                currentTypePoints = totalPoints.intValue();
            }
            //3.判断积分是否已经超过上限
            if (currentTypePoints >= type.getMaxPoints()) {
                return;
            }
            //4.判断当前分数加上获得的积分是否超过上限
            if (realPoint + currentTypePoints > type.getMaxPoints()) {
                //将剩余达到上限的分数，赋值给realPoint
                realPoint = type.getMaxPoints() - currentTypePoints;
            }
        }
        //4.保存积分
        PointsRecord record = new PointsRecord();
        record.setUserId(message.getUserId());
        record.setType(type);
        record.setPoints(realPoint);
        this.save(record);


        //5.累加并保存总积分值到redis 采用zset 当前赛季的排行榜
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        //使用zset存储
        redisTemplate.opsForZSet().incrementScore(key, message.getUserId().toString(), realPoint);
    }


    @Override
    public List<PointsStatisticsVO> queryMyTodayPoints() {
        //1.获取我的积分
        Long userId = UserContext.getUser();
        //2.查询积分表 userId today  按type分组 sum(points)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
        LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        //as后的字段要跟返回的保持一致，这里在返回对象中没有字段可以存储，所以用points来暂存
        wrapper.select("type", "sum(points) as points");
        wrapper.eq("user_id", userId);
        wrapper.between("create_time", dayStartTime, dayEndTime);
        wrapper.groupBy("type");
        List<PointsRecord> list = this.list(wrapper);
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }

        //3.封装vo返回
        List<PointsStatisticsVO> voList = new ArrayList<>();
        for (PointsRecord record : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setMaxPoints(record.getType().getMaxPoints());//积分类型上限
            vo.setType(record.getType().getDesc());//积分类型的中文
            vo.setPoints(record.getPoints()); //因为上面用这个字段暂存了积分，所以这里直接取
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public void createPointsRecordTableBySeason(Integer seasonId) {
        getBaseMapper().createPointsRecordTableBySeason(POINTS_RECORD_KEY_PREFIX + seasonId);
    }
}
