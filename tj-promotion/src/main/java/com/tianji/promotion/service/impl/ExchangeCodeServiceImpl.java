package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_RANGE_KEY;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;

    @Override
    @Async("generateExchangeCodeExecutor")
    public void asyncGenerateExchangeCode(Coupon coupon) {
        log.debug("生成兑换码 线程名 {}",Thread.currentThread().getName());
        Integer totalNum = coupon.getTotalNum();//代表优惠券发放的总数量，也就是需要生成兑换码的总数量
        //方式一：循环兑换码总数量 性能较差
        //方式二：调用incrby 批量生成兑换码
        //1.生成自增id 借助于redis incr
        Long increment = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if(increment == null){
            return;
        }
        //2.循环生成兑换码 调用工具类生成兑换码
        int maxSerialNum = increment.intValue();//本地自增id的最大值
        int begin = maxSerialNum - totalNum + 1;//自增id 循环开始值
        List<ExchangeCode> list = new ArrayList<>();
        for(int serialNum = begin; serialNum <= maxSerialNum; serialNum++){//参数一为自增id值，参数二为优惠券id(内部会计算出0-15之间的数字，然后找对应的密钥)
            String code = CodeUtil.generateCode(serialNum, coupon.getId());
            ExchangeCode exchangeCode = new ExchangeCode();
            exchangeCode.setId(serialNum);//兑换码id ExchangeCode这个po类的主键生成策略需要修改为INPUT
            exchangeCode.setCode(code);
            exchangeCode.setExchangeTargetId(coupon.getId());//优惠券id
            exchangeCode.setExpiredTime(coupon.getIssueEndTime());//兑换码 兑换的截止时间，就是优惠券领取的截止时间
            list.add(exchangeCode);
        }
        //3.将兑换码保存db exchange_code 批量保存
        this.saveBatch(list);

        // 4.写入Redis缓存，member：couponId，score：兑换码的最大序列号 (后续使用的)
        redisTemplate.opsForZSet().add(COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
    }


    @Override
    public Boolean updateExchangeCodeMark(long parsedCode, boolean flag) {
        //修改兑换码 的 自增id 对应的 offset 的值
        String key = PromotionConstants.COUPON_CODE_MAP_KEY;
        Boolean aBoolean = redisTemplate.opsForValue().setBit(key, parsedCode, flag);
        return aBoolean!=null && aBoolean;
    }

    @Override
    public PageDTO<ExchangeCodeVO> queryCodePage(CodeQuery query) {
        // 1.分页查询兑换码
        Page<ExchangeCode> page = lambdaQuery()
                .eq(ExchangeCode::getStatus, query.getStatus())
                .eq(ExchangeCode::getExchangeTargetId, query.getCouponId())
                .page(query.toMpPage());
        // 2.返回数据
        return PageDTO.of(page, c -> new ExchangeCodeVO(c.getId(), c.getCode()));
    }
}
