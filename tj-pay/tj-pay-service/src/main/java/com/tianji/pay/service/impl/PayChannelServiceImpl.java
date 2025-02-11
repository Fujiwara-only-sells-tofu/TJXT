package com.tianji.pay.service.impl;

import com.tianji.common.utils.BeanUtils;
import com.tianji.pay.sdk.dto.PayChannelDTO;
import com.tianji.pay.domain.po.PayChannel;
import com.tianji.pay.mapper.PayChannelMapper;
import com.tianji.pay.service.IPayChannelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 支付渠道 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-26
 */
@Service
@Slf4j
public class PayChannelServiceImpl extends ServiceImpl<PayChannelMapper, PayChannel> implements IPayChannelService {

    @Override
    public Long addPayChannel(PayChannelDTO channelDTO) {
        // 1.属性转换
        PayChannel payChannel = BeanUtils.toBean(channelDTO, PayChannel.class);
        // 2.保存
        save(payChannel);
        return payChannel.getId();
    }

    @Override
    public void updatePayChannel(PayChannelDTO channelDTO) {
        // 1.属性转换
        PayChannel payChannel = BeanUtils.toBean(channelDTO, PayChannel.class);
        // 2.保存
        updateById(payChannel);
    }

    @Override
    public List<PayChannel> listAllPayChannels() {
        List<PayChannel> list = null;
        try {
            list = this.lambdaQuery()
                    .eq(PayChannel::getStatus, 1)
                    .list();
        } catch (Exception e) {
            log.info("查询所有支付渠道失败", e.getMessage());
        }
        return list;
    }
}
