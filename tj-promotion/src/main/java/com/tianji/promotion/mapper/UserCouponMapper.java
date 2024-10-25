package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.promotion.enums.UserCouponStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-19
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select(" SELECT c.id, c.discount_type, c.`specific`, c.discount_value, c.threshold_amount,\n" +
            "               c.max_discount_amount, uc.id AS creater\n" +
            "        FROM user_coupon uc\n" +
            "            INNER JOIN coupon c ON uc.coupon_id = c.id\n" +
            "        WHERE uc.user_id = #{userId} AND uc.status = 1")
    List<Coupon> queryMyCoupons(@Param("userId") Long userId);


    List<Coupon> queryCouponByUserCouponIds(
            @Param("userCouponIds") List<Long> userCouponIds,
            @Param("status")  UserCouponStatus status);
}
