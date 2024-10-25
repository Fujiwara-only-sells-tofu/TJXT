package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-17
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    /*
    * 异步生成兑换码
    * */
    void asyncGenerateExchangeCode(Coupon coupon);

    /*
    * 校验兑换码是否已经被兑换
    * */
    Boolean updateExchangeCodeMark(long parsedCode, boolean flag);

    /*
    * 查询兑换码列表
    * */
    PageDTO<ExchangeCodeVO> queryCodePage(CodeQuery query);
}
