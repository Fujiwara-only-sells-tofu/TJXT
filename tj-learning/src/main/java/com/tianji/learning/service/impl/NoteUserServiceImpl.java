package com.tianji.learning.service.impl;

import com.tianji.learning.domain.po.NoteUser;
import com.tianji.learning.mapper.NoteUserMapper;
import com.tianji.learning.service.INoteUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 采用笔记表 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-24
 */
@Service
public class NoteUserServiceImpl extends ServiceImpl<NoteUserMapper, NoteUser> implements INoteUserService {

}
