package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.NoteAdminPageQuery;
import com.tianji.learning.domain.vo.NoteAdminDetailVO;
import com.tianji.learning.domain.vo.NoteAdminVO;
import com.tianji.learning.service.INoteService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


/**
 * <p>
 * 学习笔记表 前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-24
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/notes")
@Slf4j
public class NoteAdminController {

    private final INoteService noteService;

    @ApiOperation("管理端分页查询笔记")
    @GetMapping("/page")
    public PageDTO<NoteAdminVO> queryNotePageForAdmin(NoteAdminPageQuery query) {
        return noteService.queryNotePageForAdmin(query);
    }

    @ApiOperation("管理端查询笔记详情")
    @GetMapping("/{id}")
    public NoteAdminDetailVO queryNoteDetailForAdmin(
            @ApiParam(value = "笔记id", example = "1") @PathVariable("id") Long id) {
        return noteService.queryNoteDetailForAdmin(id);
    }

    @ApiOperation("隐藏指定笔记")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenNote(
            @ApiParam(value = "笔记id", example = "1") @PathVariable("id") Long id,
            @ApiParam(value = "是否隐藏", example = "false") @PathVariable("hidden") Boolean hidden) {
        noteService.hiddenNote(id, hidden);
    }
}