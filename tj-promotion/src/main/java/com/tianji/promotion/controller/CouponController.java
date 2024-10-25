package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-17
 */
@RestController
@RequestMapping("/coupons")
@Api(tags = "优惠券相关接口")
@RequiredArgsConstructor
public class CouponController {

    private final ICouponService couponService;

    @ApiOperation("新增优惠券")
    @PostMapping
    public void saveCoupon(@RequestBody @Validated CouponFormDTO dto) {
        couponService.saveCoupon(dto);
    }

    @ApiOperation("分页查询优惠券列表-管理端")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query){
        return couponService.queryCouponPage(query);

    }

    @ApiOperation("发放优惠券-客户端")
    @PutMapping("/{id}/issue")
    public void issueCoupon(@RequestBody @Validated CouponIssueFormDTO dto){
        couponService.issueCoupon(dto);
    }

    @ApiOperation("查询发放中的优惠券列表-用户端")
    @GetMapping("/list")
    public List<CouponVO> queryIssuingCoupon(){
        return couponService.queryIssuingCoupon();
    }


    @ApiOperation("修改优惠券")
    @PutMapping("/{id}")
    public void updateCoupon(@RequestBody @Validated CouponFormDTO dto) {
        couponService.updateCoupon(dto);
    }

    @ApiOperation("删除优惠券")
    @DeleteMapping("/{id}")
    public void deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
    }

    @ApiOperation("根据id查询优惠券详情")
    @GetMapping("/{id}")
    public CouponDetailVO queryCouponById(@PathVariable Long id) {
        return couponService.queryCouponById(id);
    }

    @ApiOperation("暂停发放优惠券")
    @PutMapping("/{id}/pause")
    public void pauseCoupon(@PathVariable Long id) {
        couponService.pauseCoupon(id);

    }

}
