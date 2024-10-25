package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.po.Note;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.NoteAdminPageQuery;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteAdminDetailVO;
import com.tianji.learning.domain.vo.NoteAdminVO;
import com.tianji.learning.domain.vo.NoteVO;

/**
 * <p>
 * 学习笔记表 服务类
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-24
 */
public interface INoteService extends IService<Note> {

    /*
     * 新增学习笔记
     * */
    void saveNote(NoteFormDTO dto);

    /*
     * 采集笔记
     * */
    void gatherNotes(Long id);

    /*
     * 取消采集笔记
     * */
    void cancelGatherNotes(Long id);

    /*
     * 更新笔记
     * */
    void updateNote(NoteFormDTO noteDTO);

    /*
    * 删除我的笔记
    * */
    void removeMyNote(Long id);

    /*
    * 用户端分页查询我的笔记
    * */
    PageDTO<NoteVO> queryNotePage(NotePageQuery query);

    /*
    * 客户端分页查询笔记
    * */
    PageDTO<NoteAdminVO> queryNotePageForAdmin(NoteAdminPageQuery query);

    /*
    * 管理端查询笔记详情
    * */
    NoteAdminDetailVO queryNoteDetailForAdmin(Long id);

    /*
    * 隐藏指定笔记
    * */
    void hiddenNote(Long id, Boolean hidden);
}
