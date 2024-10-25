package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.common.domain.R;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tianji.promotion.enums.CouponStatus.*;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService couponScopeService;//优惠券限定范围业务类

    private final IExchangeCodeService exchangeCodeService;//优惠券兑换码业务类

    private final IUserCouponService userCouponService;

    private final StringRedisTemplate redisTemplate;

    private final CategoryCache categoryCache;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        //1.转po 然后保存
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        this.save(coupon);
        //2.判断是否限定了范围 如果为false直接return
        if (!dto.getSpecific()) {
            return;//没有限定范围直接返回
        }
        //3.限定了范围，判断范围是否存在
        List<Long> scopes = dto.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BizIllegalException("限定范围的分类id不能为空");
        }
        //4.保存范围
        //ArrayList<CouponScope> csList = new ArrayList<>();
        //for (Long scope : scopes) {
        //    CouponScope couponScope = new CouponScope();
        //    couponScope.setCouponId(coupon.getId());
        //    couponScope.setBizId(scope);
        //    couponScope.setType(1);
        //    csList.add(couponScope);
        //}

        List<CouponScope> csList = scopes.stream()
                .map(aLong -> new CouponScope()
                        .setCouponId(coupon.getId())
                        .setBizId(aLong).setType(1))
                .collect(Collectors.toList());
        couponScopeService.saveBatch(csList);
    }


    @Override
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query) {
        //1.分页条件查询
        Page<Coupon> page = this.lambdaQuery()
                .eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //2.封装返回
        List<CouponPageVO> voList = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page, voList);
    }

    @Override
    public void issueCoupon(CouponIssueFormDTO dto) {
        log.debug("发放优惠券 线程名 {}", Thread.currentThread().getName());
        //1.校验
        if (dto.getId() == null) {
            throw new BizIllegalException("参数异常");
        }
        //2.查询优惠券
        Coupon coupon = this.getById(dto.getId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        //3.校验优惠券状态，只有待发放和暂停才能发放
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != PAUSE) {
            throw new BizIllegalException("优惠券状态错误！");
        }
        //4.判断优惠券是否立刻发放
        LocalDateTime now = LocalDateTime.now();
        //4.1该变量代表是否立刻发放
        boolean isBeginIssue = dto.getIssueBeginTime() == null || !dto.getIssueBeginTime().isAfter(now);
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 4.2.更新状态
        if (isBeginIssue) {
            c.setStatus(ISSUING);
            c.setIssueBeginTime(now);
        } else {
            c.setStatus(UN_ISSUE);
        }
        // 4.3.写入数据库
        updateById(c);

        //5.如果优惠券是立刻发放 将优惠券信息（优惠券id、领劵开始时间结束时间、发行总数量、限领数量）采用hash存入redis
        if (isBeginIssue) {
            String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId();//prs:coupon:优惠券id
            // 1.组织数据
            Map<String, String> map = new HashMap<>(4);
            map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(LocalDateTime.now())));
            map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(c.getIssueEndTime())));
            map.put("totalNum", String.valueOf(coupon.getTotalNum()));
            map.put("userLimit", String.valueOf(coupon.getUserLimit()));
            // 2.写缓存
            redisTemplate.opsForHash().putAll(key, map);
        }
        //  6.如果优惠券的领取方式为指定发放 需要生成兑换码 且 优惠券之前的状态必须为待发放
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());//兑换码的兑换截止时间就是优惠券领取的截止时间，该时间是从前端传递的
            exchangeCodeService.asyncGenerateExchangeCode(coupon);//异步生成兑换码
        }


    }


    @Override
    public List<CouponVO> queryIssuingCoupon() {
        //1.查询db 查询正在发放且手动领取的优惠券
        List<Coupon> couponList = this.lambdaQuery()
                .eq(Coupon::getStatus, ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }
        Set<Long> couponIds = couponList.stream().map(Coupon::getId).collect(Collectors.toSet());
        //2.查询用户领取表 条件是当前用户 发放中的优惠券id
        List<UserCoupon> userCouponList = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
        //2.1统计当前用户  针对每一个优惠券 的已领数量
        //Map<Long, Long> map = new HashMap<>();
        //for (UserCoupon userCoupon : userCouponList) {
        //    Long aLong = map.get(userCoupon.getCouponId());//如果map中存在该优惠券id对应的值取出来
        //    if(aLong == null){
        //        map.put(userCoupon.getCouponId(),1L);//不存在直接添加，数量为1
        //    }else {
        //        map.put(userCoupon.getCouponId(),Long.valueOf(aLong.intValue()+1));//存在的话就要加上再次取得数量
        //    }
        //}
        //这是
        /// 2.2.统计当前用户对优惠券的已经领取数量
        Map<Long, Long> issuedMap = userCouponList.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3.统计当前用户对优惠券的已经领取并且未使用的数量
        Map<Long, Long> unusedMap = userCouponList.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        //2.2 统计当前用户 针对每一个优惠券的已领 且未使用的数量
        //封装vo
        List<CouponVO> voList = new ArrayList<>();
        for (Coupon coupon : couponList) {
            CouponVO couponVO = BeanUtils.copyBean(coupon, CouponVO.class);
            //优惠券还有剩余 （issue_num < total_num） 且（统计用户卷表user_coupon取出当前已领数量<user_limit）
            Long issNum = issuedMap.getOrDefault(coupon.getId(), 0L);
            boolean avaliable = coupon.getIssueNum() < coupon.getTotalNum() && issNum.intValue() < coupon.getUserLimit();
            couponVO.setAvailable(avaliable);//是否可以领取

            //是否可以使用：当前用户已经领取并且未使用的优惠券数量 > 0
            boolean received = unusedMap.getOrDefault(coupon.getId(), 0L) > 0;
            couponVO.setReceived(received);//是否可以使用

            voList.add(couponVO);
        }
        return voList;
    }


    @Transactional
    @Override
    public void updateCoupon(CouponFormDTO dto) {
        //0.校验参数
        Long id = dto.getId();
        if (id == null) {
            throw new BadRequestException("优惠券id不能为空");
        }
        //1.只有待发送的才可以修改 查询优惠券判断状态
        Coupon coupon = this.getById(id);
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        if (coupon.getStatus() != CouponStatus.DRAFT) {
            throw new BizIllegalException("只有待发放的优惠券才可以修改");
        }
        Coupon newCoupon = BeanUtils.copyBean(dto, Coupon.class);
        this.updateById(newCoupon);
        //2.查看是否限定了范围，然后更新范围
        if (dto.getSpecific()) {
            //说明限定了范围然后更新范围表
            //先删除原来的范围 查询出所有的限定id
            List<CouponScope> scopeList = couponScopeService.lambdaQuery()
                    .eq(CouponScope::getCouponId, id)
                    .list();
            couponScopeService.removeByIds(scopeList);
            //再添加新的范围
            if (!CollUtils.isEmpty(dto.getScopes())) {
                List<CouponScope> scopes = dto.getScopes().stream().map(aLong -> new CouponScope()
                        .setCouponId(dto.getId())
                        .setBizId(aLong)
                        .setType(1)).collect(Collectors.toList());
                couponScopeService.saveBatch(scopes);
            }
        }

    }


    @Transactional
    @Override
    public void deleteCoupon(Long id) {
        //1.校验参数
        if (id == null) {
            throw new BadRequestException("优惠券id不能为空");
        }
        //2.判断状态 只有待发放的才能删除
        Coupon coupon = this.getById(id);
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        if (coupon.getStatus() != CouponStatus.DRAFT) {
            throw new BizIllegalException("只有待发放的优惠券才可以删除");
        }
        //3.查看是否限定了范围
        Boolean specific = coupon.getSpecific();
        if (specific) {
            //4.有限定 先删除优惠券 再删除范围
            this.removeById(id);
            couponScopeService.lambdaUpdate()
                    .eq(CouponScope::getCouponId, id)
                    .remove();
        }
        //5.没有限定 直接删除优惠券
        this.removeById(id);

    }


    @Override
    public CouponDetailVO queryCouponById(Long id) {
        // 1.查询优惠券
        Coupon coupon = getById(id);
        // 2.转换VO
        CouponDetailVO vo = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        if (vo == null || !coupon.getSpecific()) {
            // 数据不存在，或者没有限定范围，直接结束
            return vo;
        }
        // 3.查询限定范围
        List<CouponScope> scopes = couponScopeService.lambdaQuery().eq(CouponScope::getCouponId, id).list();
        if (CollUtils.isEmpty(scopes)) {
            return vo;
        }
        List<CouponScopeVO> scopeVOS = scopes.stream()
                .map(CouponScope::getBizId)
                .map(cateId -> new CouponScopeVO(cateId, categoryCache.getNameByLv3Id(cateId)))
                .collect(Collectors.toList());
        vo.setScopes(scopeVOS);
        return vo;
    }


    @Override
    public void pauseCoupon(Long id) {
        // 1.查询旧优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }

        // 2.当前券状态必须是未开始或进行中
        CouponStatus status = coupon.getStatus();
        if (status != UN_ISSUE && status != ISSUING) {
            // 状态错误，直接结束
            return;
        }

        // 3.更新状态
        boolean success = lambdaUpdate()
                .set(Coupon::getStatus, PAUSE)
                .eq(Coupon::getId, id)
                .in(Coupon::getStatus, UN_ISSUE, ISSUING)
                .update();
        if (!success) {
            // 可能是重复更新，结束
            log.error("重复暂停优惠券");
        }

        // 4.删除缓存
        redisTemplate.delete(PromotionConstants.COUPON_CACHE_KEY_PREFIX + id);
    }


    @Override
    public void beginIssueBatch(List<Coupon> coupons) {
        // 1.更新券状态
        for (Coupon c : coupons) {
            c.setStatus(CouponStatus.ISSUING);
        }
        updateBatchById(coupons);
        // 2.批量缓存

        for (Coupon coupon : coupons) {
            String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId();//prs:coupon:优惠券id
            // 2.1.组织数据
            Map<String, String> map = new HashMap<>(4);
            map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
            map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
            map.put("totalNum", String.valueOf(coupon.getTotalNum()));
            map.put("userLimit", String.valueOf(coupon.getUserLimit()));
            // 2.2.写缓存
            redisTemplate.opsForHash().putAll(key, map);
        }

    }

}
