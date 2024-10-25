//package com.tianji.promotion.service.impl;
//
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import com.tianji.common.autoconfigure.redisson.RedissonConfig;
//import com.tianji.common.exceptions.BadRequestException;
//import com.tianji.common.exceptions.BizIllegalException;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.promotion.domain.po.Coupon;
//import com.tianji.promotion.domain.po.ExchangeCode;
//import com.tianji.promotion.domain.po.UserCoupon;
//import com.tianji.promotion.enums.ExchangeCodeStatus;
//import com.tianji.promotion.mapper.CouponMapper;
//import com.tianji.promotion.mapper.UserCouponMapper;
//import com.tianji.promotion.service.IExchangeCodeService;
//import com.tianji.promotion.service.IUserCouponService;
//import com.tianji.promotion.utils.CodeUtil;
//import com.tianji.promotion.utils.RedisLock;
//import lombok.RequiredArgsConstructor;
//import org.redisson.api.RLock;
//import org.redisson.api.RedissonClient;
//import org.springframework.aop.framework.AopContext;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.concurrent.TimeUnit;
//
///**
// * <p>
// * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
// * </p>
// *
// * @author 张辰逸
// * @since 2024-09-19
// */
//@Service
//@RequiredArgsConstructor
//public class UserCouponRedissonServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
//
//
//    private final CouponMapper couponMapper;
//
//    private final IExchangeCodeService exchangeCodeService;
//
//    private final StringRedisTemplate redisTemplate;
//
//    private final RedissonClient redissonClient;
//
//    @Override
//    //@Transactional
//    public void receiveCoupon(Long couponId) {
//        // 1.查询优惠券
//        Coupon coupon = couponMapper.selectById(couponId);
//        if (coupon == null) {
//            throw new BadRequestException("优惠券不存在");
//        }
//        // 2.校验发放时间
//        LocalDateTime now = LocalDateTime.now();
//        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
//            throw new BadRequestException("优惠券发放已经结束或尚未开始");
//        }
//        // 3.校验库存
//        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
//            throw new BadRequestException("优惠券库存不足");
//        }
//        Long userId = UserContext.getUser();
//
//        /*//通过工具类实现分布式锁
//        String key = "lock:coupon:uid:"+userId;
//        RedisLock redisLock = new RedisLock(key,redisTemplate);
//        try {
//            boolean isLock = redisLock.tryLock(5, TimeUnit.SECONDS);
//            if (!isLock) {
//                throw new BizIllegalException("操作频繁，请稍后再试");
//            }
//            //从aop上下文中 获取当前类的代理对象
//            IUserCouponService userCoponServiceProxy = (IUserCouponService) AopContext.currentProxy();
//            //checkAndCreateUserCoupon(coupon, userId, null);//这种写法是调用原对象的写法
//            userCoponServiceProxy.checkAndCreateUserCoupon(coupon, userId, null);//这种写法是调用代理对象的写法，方法是有事务处理的
//        }finally {
//            redisLock.unlock();
//        }*/
//        //通过redisson实现分布式锁
//        String key = "lock:coupon:uid:"+userId;
//        RLock lock = redissonClient.getLock(key);//获取锁对象
//        try {
//            boolean isLock = lock.tryLock();//不传参数看门狗机制会生效，默认失效时间是30s
//            if (!isLock) {
//                throw new BizIllegalException("操作频繁，请稍后再试");
//            }
//            //从aop上下文中 获取当前类的代理对象
//            IUserCouponService userCoponServiceProxy = (IUserCouponService) AopContext.currentProxy();
//            //checkAndCreateUserCoupon(coupon, userId, null);//这种写法是调用原对象的写法
//            userCoponServiceProxy.checkAndCreateUserCoupon(coupon, userId, null);//这种写法是调用代理对象的写法，方法是有事务处理的
//        }finally {
//            lock.unlock();
//        }
//    }
//
//    private void saveUserCoupon(Coupon coupon, Long userId) {
//        // 1.基本信息
//        UserCoupon uc = new UserCoupon();
//        uc.setUserId(userId);
//        uc.setCouponId(coupon.getId());
//        // 2.有效期信息
//        LocalDateTime termBeginTime = coupon.getTermBeginTime();
//        LocalDateTime termEndTime = coupon.getTermEndTime();
//        if (termBeginTime == null) {
//            termBeginTime = LocalDateTime.now();
//            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
//        }
//        uc.setTermBeginTime(termBeginTime);
//        uc.setTermEndTime(termEndTime);
//        // 3.保存
//        save(uc);
//    }
//
//
//    @Override
//    //@Transactional//操作多张表要加上事务注解
//    public void exchangeCoupon(String code) {
//        //1.校验参数
//        if (StringUtils.isBlank(code)) {
//            throw new BadRequestException("参数不能为空");
//        }
//        //2.解析兑换码
//        long parsedCode = CodeUtil.parseCode(code);
//        //3.查看是否已经兑换 采用redis的bitmap setbit key offset 1 如果方法返回为true代表兑换码已经兑换
//        Boolean result = exchangeCodeService.updateExchangeCodeMark(parsedCode, true);
//        if (result) {
//            //说明已经被兑换了
//            throw new BizIllegalException("兑换码已经被使用");
//        }
//        try {
//            //4.判断兑换码是否存在 根据自增id查询 主键查询
//            ExchangeCode exchangeCode = exchangeCodeService.getById(parsedCode);
//            if (exchangeCode == null) {
//                throw new BadRequestException("兑换码不存在");
//            }
//            //5.查看兑换码是否过期
//            if (exchangeCode.getExpiredTime().isBefore(LocalDateTime.now())) {
//                throw new BadRequestException("兑换码已经过期");
//            }
//            //6.查询当前用户对于当前优惠券的领取记录，是否超过领取上限
//            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
//            if (coupon == null) {
//                throw new BizIllegalException("优惠券不存在");
//            }
//            Long userId = UserContext.getUser();
//            checkAndCreateUserCoupon(coupon, userId, parsedCode);
//        } catch (Exception e) {
//            //将兑换码重置
//            exchangeCodeService.updateExchangeCodeMark(parsedCode, false);
//            throw e;
//        }
//    }
//
//    @Transactional
//    public void checkAndCreateUserCoupon(Coupon coupon, Long userId, Long serialNum) {
//        //Long类型 -128~127之间是同一个对象 超出该区间则是不同的对象
//        //Long.toString() 方法的底层是new String，所以还是不同的对象
//        //Long.toString().intern() intern()方法是强制从常量池中取字符串
//        // 1.校验每人限领数量
//        // 1.1.统计当前用户对当前优惠券的已经领取的数量
//        Integer count = lambdaQuery()
//                .eq(UserCoupon::getUserId, userId)
//                .eq(UserCoupon::getCouponId, coupon.getId())
//                .count();
//        // 1.2.校验限领数量
//        if (count != null && count >= coupon.getUserLimit()) {
//            throw new BadRequestException("超出领取数量");
//        }
//        // 2.更新优惠券的已经发放的数量 + 1
//        couponMapper.incrIssueNum(coupon.getId());
//        // 3.新增一个用户券
//        saveUserCoupon(coupon, userId);
//        // 4.更新兑换码状态
//        if (serialNum != null) {
//            exchangeCodeService.lambdaUpdate()
//                    .set(ExchangeCode::getUserId, userId)
//                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
//                    .eq(ExchangeCode::getId, serialNum)
//                    .update();
//        }
//    }
//}
