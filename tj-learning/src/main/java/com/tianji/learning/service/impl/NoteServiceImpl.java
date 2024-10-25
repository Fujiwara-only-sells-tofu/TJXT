package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.Constant;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.po.Note;
import com.tianji.learning.domain.po.NoteUser;
import com.tianji.learning.domain.query.NoteAdminPageQuery;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteAdminDetailVO;
import com.tianji.learning.domain.vo.NoteAdminVO;
import com.tianji.learning.domain.vo.NoteVO;
import com.tianji.learning.mapper.NoteMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.INoteService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LEARNING_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.NOTE_GATHERED;
import static com.tianji.common.constants.MqConstants.Key.WRITE_NOTE;

/**
 * <p>
 * 学习笔记表 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-24
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements INoteService {


    private final UserClient userClient;
    private final RabbitMqHelper mqHelper;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CourseClient courseClient;

    private final CategoryCache categoryCache;


    @Override
    public void saveNote(NoteFormDTO dto) {
        //1.获取用户id
        Long userId = UserContext.getUser();
        //2.将dto转换为po
        Note note = BeanUtils.copyBean(dto, Note.class);
        note.setUserId(userId);
        note.setAuthorId(userId);
        //3.保存到数据库
        this.save(note);

        // 4.发送mq消息
        mqHelper.send(LEARNING_EXCHANGE, WRITE_NOTE, SignInMessage.of(userId, 3));
    }

    @Transactional
    @Override
    public void gatherNotes(Long id) {
        //1.校验参数
        if (id == null) {
            throw new BadRequestException("参数不能为空");
        }
        // 2.获取用户
        Long userId = UserContext.getUser();
        // 3.判断笔记是否存在 || 是否隐藏 || 是否私有
        Note note = getById(id);
        if (note == null || note.getIsPrivate() || note.getHidden()) {
            throw new BadRequestException("笔记不存在");
        }
        //4.判断是否已经采集过
        Note gatheredNote = this.lambdaQuery()
                .eq(Note::getGatheredNoteId, id)
                .eq(Note::getIsGathered, true)
                .eq(Note::getUserId, userId)
                .one();
        if(gatheredNote != null){
            throw new BizIllegalException("笔记已经采集过了");
        }
        // 4.另存一份
        note.setGatheredNoteId(note.getId());
        note.setIsGathered(true);
        note.setIsPrivate(true);
        note.setUserId(userId);
        note.setId(null);
        save(note);

        // 4.发送mq消息
        mqHelper.send(LEARNING_EXCHANGE, NOTE_GATHERED, SignInMessage.of(userId, 2));
    }

    @Override
    public void cancelGatherNotes(Long id) {
        // 1.校验参数
        if (id == null) {
            throw new BadRequestException("参数不能为空");
        }
        // 2.笔记删除条件
        Note note = this.lambdaQuery()
                .eq(Note::getUserId, UserContext.getUser())
                .eq(Note::getGatheredNoteId, id).one();
        // 3.删除笔记
        if (note == null) {
            throw new BizIllegalException("笔记不存在");
        }
        this.removeById(id);
    }

    @Override
    public void updateNote(NoteFormDTO noteDTO) {
        // 1.查询用户
        Long userId = UserContext.getUser();
        // 2.查询笔记
        Note note = getById(noteDTO.getId());
        // 不能为空
        AssertUtils.isNotNull(note, "笔记不存在");
        // 必须是自己的笔记或者自己采集的笔记
        AssertUtils.equals(note.getUserId(), userId, "笔记不存在");
        // 3.数据封装
        Note n = new Note();
        n.setId(noteDTO.getId());
        // 采集笔记不能设置公开
        if (noteDTO.getIsPrivate() != null) {
            n.setIsPrivate(note.getIsGathered() || noteDTO.getIsPrivate());
        }
        n.setContent(noteDTO.getContent());
        updateById(n);
    }

    @Override
    public void removeMyNote(Long id) {
        // 1.获取用户
        Long userId = UserContext.getUser();
        // 2.查询笔记
        Note note = getById(id);
        // 3.必须是自己的笔记才能删除
        AssertUtils.equals(userId, note.getUserId(), "无权删除他人笔记");
        // 4.删除笔记
        removeById(id);
    }


    @Override
    public PageDTO<NoteVO> queryNotePage(NotePageQuery query) {
        // 1.条件判断
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if (courseId == null && sectionId == null) {
            throw new BadRequestException("课程或小节不能为空");
        }
        Long userId = UserContext.getUser();
        //2.查询笔记
        Page<Note> p = new Page<>(query.getPageNo(), query.getPageSize());
        Page<Note> page = null;
        if (query.getOnlyMine()) {
            //只查询我的笔记
            page = queryNoteMyselfOrAll(userId, courseId, sectionId, p);
        } else {
            //查询所有人的笔记
            page = queryNoteMyselfOrAll(null, courseId, sectionId, p);
        }
        List<Note> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }

        return parseNotePages(page);
    }

    /*
     * 查询本人或者全部笔记
     * */
    private Page<Note> queryNoteMyselfOrAll(Long userId, Long courseId, Long sectionId, Page<Note> p) {

        QueryWrapper<Note> wrapper = new QueryWrapper<>();

        wrapper.lambda()
                .eq(courseId!= null,Note::getCourseId, courseId)
                .eq(sectionId!=null,Note::getSectionId, sectionId)
                .eq(Note::getHidden, false)
                .orderByAsc(sectionId != null, Note::getNoteMoment)
                .orderByDesc(sectionId == null, Note::getId);
        if (userId != null) {
            wrapper.lambda()
                    .eq(userId != null, Note::getUserId, userId);
        } else {
            wrapper.lambda().eq(Note::getIsPrivate, false);
        }

        Page<Note> page = baseMapper.selectPage(p, wrapper);
        return page;
    }


    private PageDTO<NoteVO> parseNotePages(Page<Note> page) {
        // 1.非空判断
        List<Note> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 2.查询笔记作者
        Set<Long> userIds = records.stream().map(Note::getAuthorId).collect(Collectors.toSet());
        List<UserDTO> stuInfos = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> sMap = CollUtils.isEmpty(stuInfos) ?
                new HashMap<>() :
                stuInfos.stream().collect(Collectors.toMap(UserDTO::getId, s -> s));

        // 3.处理VO
        List<NoteVO> list = new ArrayList<>(records.size());
        for (Note r : records) {
            NoteVO v = BeanUtils.toBean(r, NoteVO.class);
            UserDTO author = sMap.get(r.getAuthorId());
            if (author != null) {
                v.setAuthorId(author.getId());
                v.setAuthorName(author.getName());
                v.setAuthorIcon(author.getIcon());
            }
            v.setIsGathered(BooleanUtils.isTrue(r.getIsGathered()));
            list.add(v);
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }


    @Override
    public PageDTO<NoteAdminVO> queryNotePageForAdmin(NoteAdminPageQuery query) {
        // 1.分页条件
        Page<Note> notePage = new Page<>(query.getPageNo(), query.getPageSize());
        // 2.课程名称
        List<Long> courseIdList = null;
        if(StringUtils.isNotEmpty(query.getName())){
            // 2.1.查询课程信息
            courseIdList = searchClient.queryCoursesIdByName(query.getName());
            // 2.2.判空
            if(CollUtils.isEmpty(courseIdList)){
                return PageDTO.empty(notePage);
            }

        }
        // 3.排序条件
        if (StringUtils.isNotBlank(query.getSortBy())) {
            notePage.addOrder(new OrderItem(query.getSortBy(), query.getIsAsc()));
        } else {
            notePage.addOrder(new OrderItem(Constant.DATA_FIELD_NAME_CREATE_TIME, false));
        }

        // 4.搜索条件
        LocalDateTime beginTime = query.getBeginTime();
        LocalDateTime endTime = query.getEndTime();
        Page<Note> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(courseIdList), Note::getCourseId, courseIdList)
                .eq(query.getHidden() != null, Note::getHidden, query.getHidden())
                .ge(beginTime != null, Note::getCreateTime, beginTime)
                .le(endTime != null, Note::getCreateTime, endTime)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Note> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 6.数据处理
        List<NoteAdminVO> list = new ArrayList<>(records.size());
        // 6.1.获取问题关联的id信息
        // 课程id
        Set<Long> courseIds = new HashSet<>();
        // 章节id
        Set<Long> csIds = new HashSet<>();
        // 用户id
        Set<Long> uIds = new HashSet<>();
        for (Note r : records) {
            courseIds.add(r.getCourseId());
            if (r.getChapterId() != null) csIds.add(r.getChapterId());
            if (r.getSectionId() != null) csIds.add(r.getSectionId());
            uIds.add(r.getUserId());
        }
        // 6.1.获取课程信息
        List<CourseSimpleInfoDTO> courseInfos = courseClient.getSimpleInfoList(courseIds);
        Map<Long, String> courseMap = CollUtils.isEmpty(courseInfos) ?
                new HashMap<>() :
                courseInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, CourseSimpleInfoDTO::getName));
        // 6.2.获取章节信息
        List<CataSimpleInfoDTO> csInfos = catalogueClient.batchQueryCatalogue(csIds);
        Map<Long, String> csNameMap = CollUtils.isEmpty(csInfos) ?
                new HashMap<>() :
                csInfos.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        // 6.3.获取用户信息
        List<UserDTO> userInfos = userClient.queryUserByIds(uIds);
        Map<Long, String> userMap = CollUtils.isEmpty(userInfos) ?
                new HashMap<>() :
                userInfos.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        for (Note r : records) {
            NoteAdminVO v = BeanUtils.toBean(r, NoteAdminVO.class);
            v.setAuthorName(userMap.get(r.getUserId()));
            v.setCourseName(courseMap.get(r.getCourseId()));
            v.setChapterName(csNameMap.get(r.getChapterId()));
            v.setSectionName(csNameMap.get(r.getSectionId()));
            list.add(v);
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }

    @Override
    public NoteAdminDetailVO queryNoteDetailForAdmin(Long id) {
        // 1.查询笔记
        Note note = getById(id);
        AssertUtils.isNotNull(note, "笔记不存在");
        // 2.转换VO
        NoteAdminDetailVO vo = BeanUtils.toBean(note, NoteAdminDetailVO.class);
        // 3.查询课程信息
        CourseFullInfoDTO courseInfo =
                courseClient.getCourseInfoById(note.getCourseId(), false, false);
        if (courseInfo != null) {
            // 3.1.课程信息
            vo.setCourseName(courseInfo.getName());
            // 3.2.课程分类信息
            List<Long> cateIds = List.of(
                    courseInfo.getFirstCateId(),
                    courseInfo.getSecondCateId(),
                    courseInfo.getThirdCateId());
            String categoryNames = categoryCache.getCategoryNames(cateIds);
            vo.setCategoryNames(categoryNames);
        }
        // 4.查询章节信息
        List<CataSimpleInfoDTO> cataInfos = catalogueClient
                .batchQueryCatalogue(List.of(note.getChapterId(), note.getSectionId()));
        if (cataInfos != null && cataInfos.size() == 2) {
            for (CataSimpleInfoDTO cataInfo : cataInfos) {
                if (cataInfo.getId().equals(note.getChapterId())) {
                    vo.setChapterName(cataInfo.getName());
                } else {
                    vo.setSectionName(cataInfo.getName());
                }
            }
        }
        // 5.查询用户信息
        // 5.1.查询采集过当前笔记的用户
        List<Note> noteList = this.lambdaQuery().eq(Note::getGatheredNoteId, id).list();
         vo.setUsedTimes(noteList.size());
        Set<Long> uIds = noteList.stream().map(Note::getUserId).collect(Collectors.toSet());
        // 5.2.当前笔记作者
        Long authorId = note.getAuthorId();
        uIds.add(authorId);
        // 5.3.查询用户
        uIds.remove(0L);
        List<UserDTO> users = userClient.queryUserByIds(uIds);
        if (users != null && users.size() == uIds.size()) {
            uIds.remove(authorId);
            // 填充作者信息
            users.stream().filter(u -> u.getId().equals(authorId)).findAny().ifPresent(u -> {
                vo.setAuthorName(u.getName());
                vo.setAuthorPhone(u.getCellPhone());
            });
            // 填充采集者信息
            List<String> gathers = users.stream()
                    .filter(u -> !u.getId().equals(authorId))
                    .map(UserDTO::getName).collect(Collectors.toList());
            vo.setGathers(gathers);
        }
        return vo;
    }

    @Override
    public void hiddenNote(Long id, Boolean hidden) {
        Note note = new Note();
        note.setId(id);
        note.setHidden(hidden);
        updateById(note);
    }
}
