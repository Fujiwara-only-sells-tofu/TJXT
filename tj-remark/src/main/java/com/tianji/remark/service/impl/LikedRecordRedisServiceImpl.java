package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.msg.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.判断是点赞 还是取消点赞 liked字段 为true就是点赞
        //用于判断点赞获取取消点赞是否成功
        Boolean flag = dto.getLiked() ? liked(dto, userId) : unliked(dto, userId);
        if (!flag) {
            return;
        }
        ////3.统计改业务id下总的点赞数
        //Integer totalLikesNum = this.lambdaQuery()
        //        .eq(LikedRecord::getBizId, dto.getBizId())
        //        .count();

        //3.基于redis统计 业务id下的总点赞量
        //拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long totalLikesNum = redisTemplate.opsForSet().size(key);
        if (totalLikesNum == null) {
            return;
        }

        //4.缓存点赞的总数
        String bizTypeTotalLikeKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet().add(bizTypeTotalLikeKey, dto.getBizId().toString(), totalLikesNum);

        //4.发送消息到mq
        //LikedTimesDTO msg = LikedTimesDTO.of(dto.getBizId(), totalLikesNum.intValue());
        //log.debug("发送点赞消息 msg：{}",msg);
        //rabbitMqHelper.send(
        //        MqConstants.Exchange.LIKE_RECORD_EXCHANGE,//交换机
        //        //这里的拼接是为了在发送消息的时候指定key的，但是在接收的时候就两种，可以写死
        //        StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType()),//路由key
        //        msg);//消息


    }

    private Boolean unliked(LikeRecordFormDTO dto, Long userId) {
        //基于redis做取消赞
        //拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        return result != null && result > 0;
       /* // 1.查询点赞记录
        LikedRecord record = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, dto.getBizId())
                .one();
        // 2.判断是否存在，如果已经存在，直接结束
        if (record == null) {
            //说明之前没有点过赞
            return false;
        }
        // 3.删除点赞记录
        boolean result = this.removeById(record.getId());
        return result;*/
    }

    private Boolean liked(LikeRecordFormDTO dto, Long userId) {
        //基于redis做点赞
        //拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        //redisTemplate 往redis 的set结构中添加点赞记录
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;
        /*// 1.查询点赞记录
        LikedRecord record = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, dto.getBizId())
                .one();
        // 2.判断是否存在，如果已经存在，直接结束
        if (record != null) {
            //说明之前点过赞
            return false;
        }
        // 3.如果不存在，直接新增
        LikedRecord likeRecord = new LikedRecord();
        likeRecord.setUserId(userId);
        likeRecord.setBizId(dto.getBizId());
        likeRecord.setBizType(dto.getBizType());
        boolean result = save(likeRecord);
        return result;*/
    }


    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        //1.获取用户
        Long userId = UserContext.getUser();
        if (CollUtils.isEmpty(bizIds)) {
            return CollUtils.emptySet();
        }
        HashSet<Long> likedBizIds = new HashSet<>();
        //2.循环bizIds
        for (Long bizId : bizIds) {
            //判断该业务id 的点赞用户集合中是否包含当前用户
            Boolean member = redisTemplate.opsForSet().isMember(RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId, userId.toString());
            if (member) {
                likedBizIds.add(bizId);
            }
        }
        return likedBizIds;
    }


    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        //1.拼接key  业务类型key
        String bizTypeTotalLikeKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;

        ArrayList<LikedTimesDTO> msgList = new ArrayList<>();
        //2.从redis的zset结构中取 maxBizSize 的业务点赞信息 popmin
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().popMin(bizTypeTotalLikeKey, maxBizSize);
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String bizId = typedTuple.getValue();
            Double likedTimes = typedTuple.getScore();
            if (StringUtils.isBlank(bizId) || likedTimes == null) {
                continue;
            }
            //3.封装LikedTimesDTO 消息数据
            LikedTimesDTO msg = LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue());
            msgList.add(msg);
        }
        //4.发送消息到mq

        log.debug("发送点赞消息 msg：{}", msgList);
        if (CollUtils.isNotEmpty(msgList)) {
            rabbitMqHelper.send(
                    MqConstants.Exchange.LIKE_RECORD_EXCHANGE,//交换机
                    StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType),//路由key
                    msgList);//消息
        }
    }
}
