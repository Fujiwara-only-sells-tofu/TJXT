package com.tianji.promotion.service;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-19
 */
public interface IUserCouponService extends IService<UserCoupon> {

    /*
    * 用户领取优惠券
    * */
    void receiveCoupon(Long id);

    /*
    * 兑换码兑换优惠券
    * */
    void exchangeCoupon(String code);

    /*
    * 代理方法 检查并生成用户优惠券
    * */
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId, Long serialNum);

    /*
    * mq收到领劵消息处理逻辑
    * */
    void checkAndCreateUserCouponNew(UserCouponDTO msg);

    /*
    * 查询我的优惠券使用方案
    * */
    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses);

    /*
    * 查询我的优惠券
    * */
    PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query);

    /*
    * 根据卷方案计算订单优惠明细
    * */
    CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO);

    /*
    * 核销指定优惠券
    * */
    void writeOffCoupon(List<Long> userCouponIds);


    /*
    * 退还指定优惠券
    * */
    void refundCoupon(List<Long> userCouponIds);

    /*
    * 分页查询我使用的优惠券
    * */
    List<String> queryDiscountRules(List<Long> userCouponIds);
}
