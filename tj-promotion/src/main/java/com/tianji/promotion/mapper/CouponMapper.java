package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-17
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    //在执行前判断 发放数量和总数量的大小，如果发放数量小于总数量，则执行操作，反之代表库存不足
    @Update("UPDATE coupon SET issue_num = issue_num + 1 WHERE id = #{couponId} and issue_num < total_num")
    int incrIssueNum(@Param("couponId") Long couponId);

    int incrUsedNum(List<Long> couponIds, int i);
}
