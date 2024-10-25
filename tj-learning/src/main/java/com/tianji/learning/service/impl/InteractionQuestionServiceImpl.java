package com.tianji.learning.service.impl;

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
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-11
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;

    private final UserClient userClient;

    private final SearchClient searchClient;
    
    private final CourseClient courseClient;
    
    private final CatalogueClient catalogueClient;

    private final CategoryCache  categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.转po
        InteractionQuestion question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        question.setUserId(userId);
        //3.保存
        this.save(question);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        //1.校验参数
        if (dto.getTitle() == null || dto.getDescription() == null || dto.getAnonymity() == null) {
            throw new BadRequestException("非法参数");
        }
        //2.校验id
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("非法参数");
        }

        //只能修改自己的问题，不能修改其他人的
        Long userId = UserContext.getUser();
        if (!userId.equals(question.getUserId())) {
            throw new BadRequestException("不能修改他人的互动问题");
        }
        //3.封装参数
        question.setTitle(dto.getTitle());
        question.setDescription(dto.getDescription());
        question.setAnonymity(dto.getAnonymity());

        //4.更新
        this.updateById(question);
    }


    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        //1.校验 参数courseId
        Long courseId = query.getCourseId();
        if (courseId == null) {
            throw new BadRequestException("课程id不能为空");
        }
        //2.获取登录用户id
        Long userId = UserContext.getUser();
        //3.分页查询互动问题interaction_question
        // 条件：courseId onluMine为true才会加userId 小节id不为空 hidden为false 分页查询按提问时间倒序
        Page<InteractionQuestion> page = this.lambdaQuery()
                //通过select方法来指定不返回的字段
                .select(InteractionQuestion.class, tableFieldInfo -> !tableFieldInfo.getProperty().equals("description"))
                .eq(InteractionQuestion::getCourseId, courseId)
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(InteractionQuestion::getHidden, false)
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .page(query.toMpPage("create_time", false));
        List<InteractionQuestion> records = page.getRecords();//这是所有的互动问题
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //4.根据最新回答id，批量查询回答信息
        //互动问题的 最新回答id集合
        List<Long> latestAnswerIds = records.stream()
                .filter(c -> c.getLatestAnswerId() != null) //筛选出最新回答id不为空的
                .map(InteractionQuestion::getLatestAnswerId)
                .collect(Collectors.toList());
        //互动问题的用户id集合
        List<Long> userIds = records.stream()
                .filter(c -> !c.getAnonymity())  //筛选出非匿名的
                .map(InteractionQuestion::getUserId)
                .collect(Collectors.toList());
        //ArrayList<Long> latestAnswerIds = new ArrayList<>();
        //ArrayList<Long> userIds = new ArrayList<>();
        //for (InteractionQuestion record : records) {
        //    if(!record.getAnonymity()){
        //        userIds.add(record.getUserId());
        //    }
        //    if(record.getLatestAnswerId() != null){
        //        latestAnswerIds.add(record.getLatestAnswerId());
        //    }
        //}
        //这是所有最新回复的集合
        Map<Long, InteractionReply> replyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(latestAnswerIds)) {
            //List<InteractionReply>  replyList = interactionReplyService.listByIds(latestAnswerIds);
            List<InteractionReply> replyList = replyService.list(Wrappers.<InteractionReply>lambdaQuery()
                    .in(InteractionReply::getId, latestAnswerIds)
                    .eq(InteractionReply::getHidden, false));
            for (InteractionReply reply : replyList) {
                if (!reply.getAnonymity()) {//如果用户是匿名提问，就不需要显示用户信息，就不用加入到用户id集合
                    userIds.add(reply.getUserId());// 将最新回答的用户id添加到userIds中
                }

                replyMap.put(reply.getId(), reply);
            }
        }
        //将最新回复信息 封装到map中 key为id value为回复信息对象
        //Map<Long, InteractionReply> replyMap = replyList.stream().collect(Collectors.toMap(InteractionReply::getId, c -> c));

        //5.远程调用用户服务，批量 获取用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDtoMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //6.封装vo并返回
        ArrayList<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionVO vo = BeanUtils.copyBean(record, QuestionVO.class);
            if (!record.getAnonymity()) {
                UserDTO userDTO = userDtoMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }

            }
            InteractionReply reply = replyMap.get(record.getLatestAnswerId());
            if (reply != null) {
                if (!record.getAnonymity()) { //如果最新回复非匿名 才设置 最新回答者的名称
                    UserDTO userDTO = userDtoMap.get(reply.getUserId());
                    if (userDTO != null) {
                        vo.setLatestReplyUser(userDTO.getName());//最新回答者的名称
                    }
                }
                vo.setLatestReplyContent(reply.getContent());//最新的回答信息
            }

            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }


    @Override
    public QuestionVO queryQuestionById(Long id) {
        //1.校验参数
        if (id == null) {
            throw new BadRequestException("参数异常");
        }
        //2.根据主键查询
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("互动问题不存在");
        }
        //3.查看是否被管理员设置了隐藏
        Boolean hidden = question.getHidden();
        if (hidden) {
            return null;
        }
        //4.查看是否匿名，是的话不返回用户信息
        Boolean anonymity = question.getAnonymity();
        //5.封装vo
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        //先查询用户信息

        if (!anonymity) {
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO != null) {
                vo.setUserName(userDTO.getName());
                vo.setUserIcon(userDTO.getIcon());
            }
        }

        return vo;
    }


    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query) {
        //0.如果前端传递了课程名称，需要调用es查询课程id（由于es分词，返回是一个集合）
        String courseName = query.getCourseName();
        List<Long> cids = new ArrayList<>();
        if (StringUtils.isNotBlank(courseName)) {
            cids = searchClient.queryCoursesIdByName(courseName);//通过feign远程调用搜索服务，从es中搜索关键字对应的课程id
            if (CollUtils.isEmpty(cids)) {
                return PageDTO.empty(0L, 0L);
            }
        }
        //1.查询互动问题表 条件前端传条件了就添加条件 分页排序按提问时间倒序
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(cids),InteractionQuestion::getCourseId, cids)
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .gt(query.getBeginTime() != null, InteractionQuestion::getCreateTime, query.getBeginTime())
                .lt(query.getEndTime() != null, InteractionQuestion::getCreateTime, query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);//如果查询结果为空 直接返回空对象
        }
        //定义集合存放用户id 课程id 章节和小节id
        Set<Long> userIds = new HashSet<>();
        Set<Long> courseIds = new HashSet<>();
        Set<Long> chapterAndSectionIds = new HashSet<>();
        for (InteractionQuestion record : records) {
            userIds.add(record.getUserId());
            courseIds.add(record.getCourseId());
            chapterAndSectionIds.add(record.getChapterId());
            chapterAndSectionIds.add(record.getSectionId());
        }
        //2.远程调用用户服务 获取用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        if(CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户信息不存在");
        }
        Map<Long, UserDTO> userDtoMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //3.远程调用课程服务 获取课程信息
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cinfos)){
            throw new BizIllegalException("课程信息不存在");
        }
        Map<Long, CourseSimpleInfoDTO> cinfoMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        //4.远程调用课程服务 获取章节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if(CollUtils.isEmpty(cataSimpleInfoDTOS)){
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cateInfoDto = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));



        //6.封装vo返回
        List<QuestionAdminVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionAdminVO adminVO = BeanUtils.copyBean(record, QuestionAdminVO.class);
            UserDTO userDTO = userDtoMap.get(record.getUserId());
            if(userDTO != null){
                adminVO.setUserName(userDTO.getName());
            }
            CourseSimpleInfoDTO cinfoDto = cinfoMap.get(record.getCourseId());
            if(cinfoDto != null){
                adminVO.setCourseName(cinfoDto.getName());
                //5.获取分类信息
                List<Long> categoryIds = cinfoDto.getCategoryIds();
                String categoryNames = categoryCache.getCategoryNames(categoryIds);
                adminVO.setCategoryName(categoryNames); //三级分类名称，拼接起来
            }
            adminVO.setChapterName(cateInfoDto.get(record.getChapterId()));
            adminVO.setSectionName(cateInfoDto.get(record.getSectionId()));

            voList.add(adminVO);
        }
        return PageDTO.of(page,voList);
    }

    @Override
    public void deleteQuestionById(Long id) {
        //0.校验参数
        if(id == null){
            throw new BadRequestException("参数不能为空");
        }
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.查询问题
        InteractionQuestion question = this.getById(id);
        if(question == null){
            throw new BizIllegalException("问题不存在");
        }
        //3.删除问题
        if (!question.getUserId().equals(userId)) {
            throw new BizIllegalException("不能删除别人的问题");
        }
        this.removeById(id);
        //4.删除问题下的回答和评论
        List<InteractionReply> replyList = replyService.lambdaQuery()
                .eq(InteractionReply::getQuestionId, id)
                .list();
        //获取所有评论的id
        List<Long> replyIdList = replyList.stream().map(InteractionReply::getId).collect(Collectors.toList());
        replyService.removeByIds(replyIdList);
    }

    @Override
    public void hiddenQuestion(Long id, Boolean hidden) {
        //1.校验参数
        if(id==null || hidden ==null){
            throw new BadRequestException("参数异常");
        }
        //2.查询问题
        InteractionQuestion question = this.getById(id);
        if(question  == null){
            throw new BizIllegalException("问题不存在");
        }

        //3.设置隐藏或显示
        question.setHidden(hidden);
        this.updateById(question);
    }

    @Override
    public QuestionAdminVO queryQuestion(Long id) {
        //1.校验参数
        if(id==null){
            throw new BadRequestException("参数异常");
        }
        //2.查询问题
        InteractionQuestion question = this.lambdaQuery()
                .eq(InteractionQuestion::getId, id)
                .one();
        if(question == null){
            throw new BizIllegalException("问题不存在");
        }
        //3.查询当前用户id
        Long userId = question.getUserId();
        //4.远程调用用户管理查询用户信息
        UserDTO userDTO = userClient.queryUserById(userId);
        if(userDTO == null){
            throw new BizIllegalException("用户信息不存在");
        }
        //5.远程调用课程管理查询课程信息
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(question.getCourseId(), true, true);
        if(cinfo == null){
            throw new BizIllegalException("课程信息不存在");
        }
        //获取三级分类id
        List<Long> categoryIds = cinfo.getCategoryIds();
        String categoryNames = categoryCache.getCategoryNames(categoryIds);
        //获取章节信息id
        Long chapterId = question.getChapterId();
        Long sectionId = question.getSectionId();
        ArrayList<Long> chapterIdAndSectionId = new ArrayList<>();
        chapterIdAndSectionId.add(chapterId);
        chapterIdAndSectionId.add(sectionId);
        //获取目录信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterIdAndSectionId);
        if(CollUtils.isEmpty(cataSimpleInfoDTOS)){
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cateInfoDto = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));


        List<Long> teacherIds = cinfo.getTeacherIds();
        UserDTO teacher = userClient.queryUserById(teacherIds.get(0));
        //6.封装vo返回
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        vo.setUserName(userDTO.getName());
        vo.setUserIcon(userDTO.getIcon());
        vo.setCourseName(cinfo.getName());
        vo.setCategoryName(categoryNames);
        vo.setChapterName(cateInfoDto.get(chapterId));
        vo.setSectionName(cateInfoDto.get(sectionId));
        vo.setTeacherName(teacher.getName());
        return vo;
    }
}
