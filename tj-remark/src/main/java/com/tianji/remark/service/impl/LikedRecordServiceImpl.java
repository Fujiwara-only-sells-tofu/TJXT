//package com.tianji.remark.service.impl;
//
//import com.tianji.api.msg.LikedTimesDTO;
//import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
//import com.tianji.common.constants.MqConstants;
//import com.tianji.common.exceptions.BadRequestException;
//import com.tianji.common.utils.CollUtils;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.remark.domain.dto.LikeRecordFormDTO;
//import com.tianji.remark.domain.po.LikedRecord;
//import com.tianji.remark.mapper.LikedRecordMapper;
//import com.tianji.remark.service.ILikedRecordService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * <p>
// * 点赞记录表 服务实现类
// * </p>
// *
// * @author 张辰逸
// * @since 2024-09-13
// */
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//    private final RabbitMqHelper rabbitMqHelper;
//
//    @Override
//    public void addLikeRecord(LikeRecordFormDTO dto) {
//        //1.获取当前用户id
//        Long userId = UserContext.getUser();
//        //2.判断是点赞 还是取消点赞 liked字段 为true就是点赞
//        //用于判断点赞获取取消点赞是否成功
//        Boolean flag = dto.getLiked() ? liked(dto, userId) : unliked(dto, userId);
//        if(!flag ){
//            return;
//        }
//        //3.统计改业务id下总的点赞数
//        Integer totalLikesNum = this.lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .count();
//        //4.发送消息到mq
//        LikedTimesDTO msg = LikedTimesDTO.of(dto.getBizId(), totalLikesNum);
//        log.debug("发送点赞消息 msg：{}",msg);
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,//交换机
//                //这里的拼接是为了在发送消息的时候指定key的，但是在接收的时候就两种，可以写死
//                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType()),//路由key
//                msg);//消息
//
//    }
//
//    private Boolean unliked(LikeRecordFormDTO dto, Long userId) {
//        // 1.查询点赞记录
//        LikedRecord record = lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        // 2.判断是否存在，如果已经存在，直接结束
//        if (record == null) {
//            //说明之前没有点过赞
//            return false;
//        }
//        // 3.删除点赞记录
//        boolean result = this.removeById(record.getId());
//        return result;
//    }
//
//    private Boolean liked(LikeRecordFormDTO dto, Long userId) {
//        // 1.查询点赞记录
//        LikedRecord record = lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        // 2.判断是否存在，如果已经存在，直接结束
//        if (record != null) {
//            //说明之前点过赞
//            return false;
//        }
//        // 3.如果不存在，直接新增
//        LikedRecord likeRecord = new LikedRecord();
//        likeRecord.setUserId(userId);
//        likeRecord.setBizId(dto.getBizId());
//        likeRecord.setBizType(dto.getBizType());
//        boolean result = save(likeRecord);
//        return result;
//    }
//
//
//    @Override
//    public Set<Long> isBizLiked(List<Long> bizIds) {
//        //1.校验参数
//        if (CollUtils.isEmpty(bizIds)) {
//            throw new BadRequestException("参数异常");
//        }
//        //2.获取当前用户id
//        Long userId = UserContext.getUser();
//        //3.批量查询点赞的业务id
//        List<LikedRecord> records = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .in(LikedRecord::getBizType, bizIds)
//                .list();
//        if(CollUtils.isEmpty(records)){
//            return Set.of();
//        }
//        Set<Long> ids = records.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
//        return ids;
//    }
//}
