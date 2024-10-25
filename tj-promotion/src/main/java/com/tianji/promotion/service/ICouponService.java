package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-17
 */
public interface ICouponService extends IService<Coupon> {

    /*
    * 新增优惠券
    * */
    void saveCoupon(CouponFormDTO dto);

    /*
    * 管理端分页查询优惠券
    *
    * */
    PageDTO<CouponPageVO> queryCouponPage(CouponQuery query);

    /*
    * 发放优惠券
    * */
    void issueCoupon(CouponIssueFormDTO dto);

    /*
    查询发放中的优惠券
    * */
    List<CouponVO> queryIssuingCoupon();

    /*
    * 修改优惠券
    * */
    void updateCoupon(CouponFormDTO dto);

    /*
    * 删除优惠券
    * */
    void deleteCoupon(Long id);

    /*
    * 根据id查询优惠券详情
    * */
    CouponDetailVO queryCouponById(Long id);

    /*
    * 暂停优惠券发放
    * */
    void pauseCoupon(Long id);

    /*
    * 定时开始发放优惠券
    * */
    void beginIssueBatch(List<Coupon> records);
}
