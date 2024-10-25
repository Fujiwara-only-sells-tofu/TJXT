package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.*;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {


    private final CouponMapper couponMapper;

    private final IExchangeCodeService exchangeCodeService;

    private final StringRedisTemplate redisTemplate;

    private final RedissonClient redissonClient;

    private final RabbitMqHelper rabbitMqHelper;

    private final ICouponScopeService couponScopeService;

    private final Executor calculteSolutionExecutor;


    @Override
    //@Transactional
    //分布式锁可以对优惠券加锁
    @MyLock(name = "lock:coupon:uid:#{couponId}")
    public void receiveCoupon(Long couponId) {
        // 1.查询优惠券
        //Coupon coupon = couponMapper.selectById(couponId);
        Coupon coupon = queryCouponByCache(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }

        // 2.校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 3.校验库存
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        Long userId = UserContext.getUser();

        //统计已经领取的数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        //increment 代表本次领取后的 已领数量
        Long increment = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        //校验是否超过限领数量
        if (increment > coupon.getUserLimit()) {//由于increment是+1之后的结果，所以此处只能判断大于 不能等于
            throw new BizIllegalException("领取次数过多");
        }
        //修改已经领取数量+1
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        redisTemplate.opsForHash().increment(couponKey, "totalNum", -1);

        //发送消息到mq 消息的内容为 userId couponId

        UserCouponDTO msg = new UserCouponDTO();
        msg.setCouponId(couponId);
        msg.setUserId(userId);
        rabbitMqHelper.send(
                MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,
                msg);


        /*//从aop上下文中 获取当前类的代理对象
        IUserCouponService userCoponServiceProxy = (IUserCouponService) AopContext.currentProxy();
        //checkAndCreateUserCoupon(coupon, userId, null);//这种写法是调用原对象的写法
        userCoponServiceProxy.checkAndCreateUserCoupon(coupon, userId, null);//这种写法是调用代理对象的写法，方法是有事务处理的*/

    }

    /*
     * 从redis获取优惠券信息（领取的开始和结束时间、发行总数量、限领数量）
     * */
    private Coupon queryCouponByCache(Long couponId) {
        //1.拼接key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        //2.从redis中获取数据
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Coupon coupon = BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());
        return coupon;
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        // 1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 2.有效期信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        // 3.保存
        save(uc);
    }


    @Override
    //@Transactional//操作多张表要加上事务注解
    public void exchangeCoupon(String code) {
        //1.校验参数
        if (StringUtils.isBlank(code)) {
            throw new BadRequestException("参数不能为空");
        }
        //2.解析兑换码
        long parsedCode = CodeUtil.parseCode(code);
        //3.查看是否已经兑换 采用redis的bitmap setbit key offset 1 如果方法返回为true代表兑换码已经兑换
        Boolean result = exchangeCodeService.updateExchangeCodeMark(parsedCode, true);
        if (result) {
            //说明已经被兑换了
            throw new BizIllegalException("兑换码已经被使用");
        }
        try {
            //4.判断兑换码是否存在 根据自增id查询 主键查询
            ExchangeCode exchangeCode = exchangeCodeService.getById(parsedCode);
            if (exchangeCode == null) {
                throw new BadRequestException("兑换码不存在");
            }
            //5.查看兑换码是否过期
            if (exchangeCode.getExpiredTime().isBefore(LocalDateTime.now())) {
                throw new BadRequestException("兑换码已经过期");
            }
            //6.查询当前用户对于当前优惠券的领取记录，是否超过领取上限
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            if (coupon == null) {
                throw new BizIllegalException("优惠券不存在");
            }
            Long userId = UserContext.getUser();
            checkAndCreateUserCoupon(coupon, userId, parsedCode);
        } catch (Exception e) {
            //将兑换码重置
            exchangeCodeService.updateExchangeCodeMark(parsedCode, false);
            throw e;
        }
    }

    @Transactional
    @MyLock(name = "lock:coupon:uid:#{userId}",
            waitTime = 1,
            leaseTime = 5,
            unit = TimeUnit.SECONDS,
            lockType = MyLockType.RE_ENTRANT_LOCK,
            lockStrategy = MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT)
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId, Long serialNum) {

        // 1.校验每人限领数量
        // 1.1.统计当前用户对当前优惠券的已经领取的数量
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        // 1.2.校验限领数量
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("超出领取数量");
        }
        // 2.更新优惠券的已经发放的数量 + 1
        couponMapper.incrIssueNum(coupon.getId());
        // 3.新增一个用户券
        saveUserCoupon(coupon, userId);
        // 4.更新兑换码状态
        if (serialNum != null) {
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
    }

    @Transactional
    @Override
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {
        //1.从db中查询优惠券
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if (coupon == null) {
            return;
        }

        // 2.更新优惠券的已经发放的数量 + 1
        int num = couponMapper.incrIssueNum(coupon.getId());
        if (num == 0) {
            return;
        }
        // 3.新增一个用户券
        saveUserCoupon(coupon, msg.getUserId());
        // 4.更新兑换码状态
        //if (serialNum != null) {
        //    exchangeCodeService.lambdaUpdate()
        //            .set(ExchangeCode::getUserId, userId)
        //            .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
        //            .eq(ExchangeCode::getId, serialNum)
        //            .update();
        //}
    }


    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
        //1.查询当前用户可用的优惠券 coupon和user_coupon表 条件 userid status=1 查哪些字段 优惠券的规则 优惠券id 用户卷id
        List<Coupon> coupons = getBaseMapper().queryMyCoupons(UserContext.getUser());
        if (coupons == null) {
            return Collections.emptyList();
        }
        log.debug("当前用户的优惠券有 {} 张", coupons.size());
        for (Coupon coupon : coupons) {
            log.debug("初筛前优惠券:{}, {}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);
        }
        //2.初筛
        //2.1计算订单的总金额 对courses的price累加
        int totalAmount = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        log.debug("当前订单的总金额为 {}", totalAmount);
        //2.2校验优惠券是否可用
        List<Coupon> availableCoupons = coupons.stream()
                .filter(
                        coupon -> DiscountStrategy
                                .getDiscount(coupon.getDiscountType())
                                .canUse(totalAmount, coupon)
                )
                .collect(Collectors.toList());
        log.debug("经过初筛后优惠券有 {} 张", availableCoupons.size());
        for (Coupon coupon : availableCoupons) {
            log.debug("初筛前优惠券:{}, {}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);
        }
        //3.细筛（需要考虑优惠券的限定范围） 排列组合
        Map<Coupon, List<OrderCourseDTO>> avaMap = findAvailableCoupon(availableCoupons, orderCourses);
        if (avaMap.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Map.Entry<Coupon, List<OrderCourseDTO>>> entries = avaMap.entrySet();
        for (Map.Entry<Coupon, List<OrderCourseDTO>> entry : entries) {
            log.debug("初筛前优惠券:{}, {}",
                    DiscountStrategy.getDiscount(entry.getKey().getDiscountType()).getRule(entry.getKey()),
                    entry.getKey());
            List<OrderCourseDTO> value = entry.getValue();
            for (OrderCourseDTO orderCourseDTO : value) {
                log.debug("可用课程 {}", orderCourseDTO);
            }
        }
        availableCoupons = new ArrayList<>(avaMap.keySet());//才是真正可用的优惠券集合
        log.debug("经过细筛之后的信息 优惠券个数 {}", availableCoupons.size());
        for (Coupon coupon : availableCoupons) {
            log.debug("优惠券:{}, {}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);
        }
        //排列组合
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        for (Coupon availableCoupon : availableCoupons) {
            solutions.add(List.of(availableCoupon));//添加单卷到方案中
        }
        log.debug("排列组合");
        for (List<Coupon> solution : solutions) {
            List<Long> cids = solution.stream().map(Coupon::getId).collect(Collectors.toList());
            log.debug("{}", cids);
        }

   /*     //4.计算出每一种的优惠明细
        log.debug("开始计算 每一种组合的优惠明细");
        List<CouponDiscountDTO> dtos = new ArrayList<>();
        for (List<Coupon> solution : solutions) {
            CouponDiscountDTO dto = calculateSolutionDiscount(avaMap, orderCourses, solution);
            log.debug("方案最终优惠 {} 方案中优惠券使用了 {} 规则{}", dto.getDiscountAmount(), dto.getIds(), dto.getRules());
            dtos.add(dto);
        }*/
        //5.使用多线程改造第4步 并行计算每一种组合的优惠明细
        log.debug("多线程  开始计算 每一种组合的优惠明细");
        //List<CouponDiscountDTO> dtos = new ArrayList<>();
        List<CouponDiscountDTO> dtos = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        CountDownLatch latch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions) {
            CompletableFuture.supplyAsync(new Supplier<CouponDiscountDTO>() {
                @Override
                public CouponDiscountDTO get() {
                    CouponDiscountDTO dto = calculateSolutionDiscount(avaMap, orderCourses, solution);
                    return dto;
                }
            },calculteSolutionExecutor).thenAccept(new Consumer<CouponDiscountDTO>() {
                @Override
                public void accept(CouponDiscountDTO couponDiscountDTO) {
                    log.debug("方案最终优惠 {} 方案中优惠券使用了 {} 规则{}", couponDiscountDTO.getDiscountAmount(), couponDiscountDTO.getIds(),
                            couponDiscountDTO.getRules());
                    dtos.add(couponDiscountDTO);
                    latch.countDown();//计数器减一
                }
            });
        }
        try {
            latch.await(2,TimeUnit.SECONDS);//主线程最多阻塞2秒
        } catch (InterruptedException e) {
            log.error("多线程计算组合优惠明细 报错了",e);
        }

        //6.筛选最优解

        return findBestSolution(dtos);

    }

    /*
    * 求最优解
    * -用卷相同时，优惠金额最高的方案
    * -优惠金额相同时，用卷最少得方案
    * */
    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> solutions) {
        //1.创建两个map 分别记录 用卷相同 金额最高 金额相同 用卷最少
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();
        //2.循环方案 向map中记录 用卷相同 金额最高 金额相同 用卷最少
        for (CouponDiscountDTO solution : solutions) {
            //2.1对优惠券id 升序 转字符串 然后逗号拼接
            String ids = solution.
                    getIds()
                    .stream()
                    .sorted(Comparator.comparing(Long::longValue))
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            //2.2从moreDiscountMap中取 旧的记录 判断 如果当前方案的优惠金额 小于 旧的方案金额 当前方案忽略 处理下一个方案
            CouponDiscountDTO old = moreDiscountMap.get(ids);
            if(old != null && old.getDiscountAmount() >= solution.getDiscountAmount()){
                continue;
            }
            //2.3从lessCouponMap中取 旧的记录 判断 如果当前方案的优惠券数量 大于 旧的方案优惠券数量 当前方案忽略 处理下一个方案
            old = lessCouponMap.get(solution.getDiscountAmount());
            int newSize = solution.getIds().size();//新的方案的 用卷数量
            if(old != null && newSize>1 && old.getIds().size() <= newSize){
                continue;
            }
            //2.4添加新的方案到map中
            moreDiscountMap.put(ids, solution);
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }
        //3.求两个map的交集
        Collection<CouponDiscountDTO> bestSolutions = CollUtils
                .intersection(moreDiscountMap.values(), lessCouponMap.values());
        //4.对最终的方案结果 按优惠金额 倒序
        return bestSolutions.stream()
                .sorted(Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
    }

    /*
     * 计算每一种组合的优惠信息
     * */
    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> avaMap, List<OrderCourseDTO> orderCourses, List<Coupon> solution) {
        //1.创建方案结果dto对象
        CouponDiscountDTO dto = new CouponDiscountDTO();
        //2.初始化商品id和商品折扣明细的映射，初始折扣明细全都设置为0
        Map<Long, Integer> detailMap = orderCourses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, orderCourseDTO -> 0));
        dto.setDiscountDetail(detailMap);
        //3.计算该方案的优惠信息
        //3.1循环方案中优惠券
        for (Coupon coupon : solution) {
            //3.2去除该优惠券对应的可用课程
            List<OrderCourseDTO> availableCourses = avaMap.get(coupon);
            //3.3计算可用课程的总金额（商品价格-该商品的折扣明细）
            int totalAmount = availableCourses.stream()
                    .mapToInt(value -> value.getPrice() - detailMap.get(value.getId()))
                    .sum();
            //3.4判断优惠券是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)) {
                continue;//卷不可用，继续下一个优惠券的处理
            }
            //3.5计算该优惠券使用后的折扣值
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            //3.6更新商品的折扣明细 更新到detailMap中
            calculateDetailDiscount(detailMap, availableCourses, totalAmount, discountAmount);
            //3.7累加每一个优惠券的优惠金额 赋值给方案结果dto对象
            dto.getIds().add(coupon.getId());//只要执行到这里，说明该优惠券可用，那么就添加到方案结果dto对象中
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount());//不能覆盖，应该是所有生效的优惠券累加的结果
        }
        return dto;
    }

    /*
     * 计算商品折扣明细
     * */
    private void calculateDetailDiscount(Map<Long, Integer> detailMap, List<OrderCourseDTO> availableCourses, int totalAmount, int discountAmount) {
        //目的：本方法就是优惠券在使用后 计算每一个商品的折扣明细
        //规则：前面的商品按比例计算  最后一个商品折扣明细 = 总的优惠金额 - 前面商品优惠的总额
        //循环可用商品
        int times = 0;//代表已经处理的商品个数
        int remainDiscount = discountAmount;//代表剩余的优惠金额
        for (OrderCourseDTO c : availableCourses) {
            times++;
            int discount = 0;
            if (times == availableCourses.size()) {
                //说明是最后一个课程
                discount = remainDiscount;
            } else {
                //是前面的课程 按比例
                discount = c.getPrice() * discountAmount / totalAmount;//此处先乘后除否则结果是0
                remainDiscount -= discount;
            }
            //将商品的折扣明细添加到 detailmap 累加
            detailMap.put(c.getId(), detailMap.get(c.getId()) + discount);
        }
    }

    /*
     * 细筛，查询每一个优惠券对应的可用课程
     * */

    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(List<Coupon> coupons,
                                                                  List<OrderCourseDTO> orderCourses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>();
        //1.循环遍历初筛后的优惠券集合
        for (Coupon coupon : coupons) {
            //2.找出每一个优惠券的可用课程
            List<OrderCourseDTO> availableCourses = orderCourses;
            //2.1判断优惠券是否限定了范围，coupon.specific为true
            if (coupon.getSpecific()) {
                //2.2查询限定范围 查询coupon_scope表 条件coupon_id
                List<CouponScope> scopeList = couponScopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId()).list();
                //2.3得到限定范围的id集合
                List<Long> scopeIds = scopeList.stream().map(CouponScope::getBizId).collect(Collectors.toList());
                //2.4从orderCourses订单中所有的课程集合 筛选 该范围内的课程
                availableCourses = orderCourses.stream().filter(orderCourseDTO -> scopeIds.contains(orderCourseDTO.getCateId()))
                        .collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourses)) {
                continue;//说明当前优惠券限定了范围，但是没有可用课程，说明该卷不可用，忽略改卷，进行下一个优惠券的处理
            }

            //3.计算该优惠券，可用课程的总金额
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            //4.判断该优惠券是否可用，如果可用添加到map中
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmount, coupon)) {
                map.put(coupon, availableCourses);
            }
        }

        return map;
    }


    @Override
    public PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query) {
        //1.获取当前用户  userId
        Long userId = UserContext.getUser();
        //2.根据参数查询
        Page<UserCoupon> page = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(query.getStatus() != null, UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<UserCoupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(0L,0L);
        }
        //3.将用户优惠券的优惠券id全都收集起来
        List<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toList());
        //4.调用couponmapper查询出所有的优惠券信息
        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);
        if(CollUtils.isEmpty(coupons)){
            return PageDTO.empty(0L,0L);
        }
        //封装voList
        List<CouponVO> voList = BeanUtils.copyList(coupons, CouponVO.class);
        return PageDTO.of(page,voList);
    }


    @Override
    public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
        // 1.查询用户优惠券
        List<Long> userCouponIds = orderCouponDTO.getUserCouponIds();
        List<Coupon> coupons = getBaseMapper().queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.UNUSED);
        if (CollUtils.isEmpty(coupons)) {
            return null;
        }
        // 2.查询优惠券对应课程
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(coupons, orderCouponDTO.getCourseList());
        if (CollUtils.isEmpty(availableCouponMap)) {
            return null;
        }
        // 3.查询优惠券规则
        return calculateSolutionDiscount(availableCouponMap, orderCouponDTO.getCourseList(), coupons);
    }

    @Override
    @Transactional
    public void writeOffCoupon(List<Long> userCouponIds) {
        // 1.查询优惠券
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) {
            return;
        }
        // 2.处理数据
        List<UserCoupon> list = userCoupons.stream()
                // 过滤无效券
                .filter(coupon -> {
                    if (coupon == null) {
                        return false;
                    }
                    if (UserCouponStatus.UNUSED != coupon.getStatus()) {
                        return false;
                    }
                    LocalDateTime now = LocalDateTime.now();
                    return !now.isBefore(coupon.getTermBeginTime()) && !now.isAfter(coupon.getTermEndTime());
                })
                // 组织新增数据
                .map(coupon -> {
                    UserCoupon c = new UserCoupon();
                    c.setId(coupon.getId());
                    c.setStatus(UserCouponStatus.USED);
                    return c;
                })
                .collect(Collectors.toList());

        // 4.核销，修改优惠券状态
        boolean success = updateBatchById(list);
        if (!success) {
            return;
        }
        // 5.更新已使用数量
        List<Long> couponIds = userCoupons.stream().map(UserCoupon::getCouponId).collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds, 1);
        if (c < 1) {
            throw new DbException("更新优惠券使用数量失败！");
        }
    }


    @Override
    @Transactional
    public void refundCoupon(List<Long> userCouponIds) {
        // 1.查询优惠券
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) {
            return;
        }
        // 2.处理优惠券数据
        List<UserCoupon> list = userCoupons.stream()
                // 过滤无效券
                .filter(coupon -> coupon != null && UserCouponStatus.USED == coupon.getStatus())
                // 更新状态字段
                .map(coupon -> {
                    UserCoupon c = new UserCoupon();
                    c.setId(coupon.getId());
                    // 3.判断有效期，是否已经过期，如果过期，则状态为 已过期，否则状态为 未使用
                    LocalDateTime now = LocalDateTime.now();
                    UserCouponStatus status = now.isAfter(coupon.getTermEndTime()) ?
                            UserCouponStatus.EXPIRED : UserCouponStatus.UNUSED;
                    c.setStatus(status);
                    return c;
                }).collect(Collectors.toList());

        // 4.修改优惠券状态
        boolean success = updateBatchById(list);
        if (!success) {
            return;
        }
        // 5.更新已使用数量
        List<Long> couponIds = userCoupons.stream().map(UserCoupon::getCouponId).collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds, -1);
        if (c < 1) {
            throw new DbException("更新优惠券使用数量失败！");
        }
    }


    @Override
    public List<String> queryDiscountRules(List<Long> userCouponIds) {
        // 1.查询优惠券信息
        List<Coupon> coupons = baseMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.USED);
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2.转换规则
        return coupons.stream()
                .map(c -> DiscountStrategy.getDiscount(c.getDiscountType()).getRule(c))
                .collect(Collectors.toList());
    }
}
