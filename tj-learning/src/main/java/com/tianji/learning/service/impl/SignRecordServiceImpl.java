package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
    @Override
    public SignResultVO addSignRecords() {
        //1.获取当前用户
        Long userId = UserContext.getUser();
        //2.拼接key
        LocalDate now = LocalDate.now();//获取当前日期
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));//转格式
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;//获得rediskey
        //3.利用bitmap命令 将签到命令保存到redis的bitmap结构中 需要校验是否已经签到过
        int offset = now.getDayOfMonth() - 1;//偏移量 当天在本月天数-1
        Boolean setBit = redisTemplate.opsForValue().setBit(key, offset, true);
        if (setBit) {
            throw new BizIllegalException("不能重复签到");
        }
        //4.计算连续签到过的天数
        int days = countSignDays(key, now.getDayOfMonth());
        //5.计算连续签到  奖励积分
        int rewards = 0;
        switch (days) {
            case 7:
                rewards = 10;
                break;
            case 14:
                rewards = 20;
                break;
            case 28:
                rewards = 40;
                break;
        }
        //6.保存积分
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewards + 1));// 签到积分是基本得分+奖励积分
        //返回vo
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(days);
        vo.setRewardPoints(rewards);
        return vo;
    }

    /*
     *计算连续签到的天数
     *
     * */
    private int countSignDays(String key, int dayOfMonth) {
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if (CollUtils.isEmpty(bitField)) {
            return 0;
        }
        Long num = bitField.get(0);//本月第一天到这一天的签到的天数，十进制
        int counter = 0;//计算器，记录连续签到的天数
        //计算连续签到的天数
        while ((num & 1) == 1) {
            counter++;
            num = num >>> 1;//右移一位
        }
        return counter;
    }


    @Override
    public Byte[] querySignRecords() {
        //1.获取当前用户
        Long userId = UserContext.getUser();
        //2.拼接key
        LocalDate now = LocalDate.now();//获取当前日期
        int dayOfMonth = now.getDayOfMonth();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));//转格式
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        //3.查询
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands
                                .BitFieldType
                                .unsigned(dayOfMonth))
                        .valueAt(0));;
        if(CollUtils.isEmpty(bitField)){
            return new Byte[0];
        }
        int num = bitField.get(0).intValue();

        Byte[] arr = new Byte[dayOfMonth];
        int pos = dayOfMonth - 1;
        while (pos >= 0){
            arr[pos--] = (byte)(num & 1);
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return arr;
    }
}
