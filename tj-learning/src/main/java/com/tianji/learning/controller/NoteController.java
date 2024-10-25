package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteVO;
import com.tianji.learning.service.INoteService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 学习笔记表 前端控制器
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-24
 */
@RestController
@RequestMapping("/notes")
@Slf4j
@RequiredArgsConstructor
public class NoteController {

    private final INoteService noteService;

    @ApiOperation("新增学习笔记")
    @PostMapping
    public void saveNote(@RequestBody NoteFormDTO dto){
        noteService.saveNote(dto);
    }

    @ApiOperation("采集笔记")
    @PostMapping("/gathers/{id}")
    public void gatherNotes(@PathVariable("id") Long id){
        noteService.gatherNotes(id);
    }

    @ApiOperation("取消采集笔记")
    @DeleteMapping("/gathers/{id}")
    public void cancelGatherNotes(@PathVariable("id") Long id){
        noteService.cancelGatherNotes(id);
    }


    @ApiOperation("更新笔记")
    @PutMapping("/{id}")
    public void updateNote(
            @ApiParam(value = "笔记id", example = "1") @PathVariable("id") Long id,
            @RequestBody NoteFormDTO noteDTO) {
        noteDTO.setId(id);
        noteService.updateNote(noteDTO);
    }

    @ApiOperation("删除我的笔记")
    @DeleteMapping("/{id}")
    public void removeMyNote(@ApiParam(value = "笔记id", example = "1") @PathVariable("id") Long id) {
        noteService.removeMyNote(id);
    }

    @ApiOperation("用户端分页查询笔记")
    @GetMapping("/page")
    public PageDTO<NoteVO> queryNotePage(@Valid NotePageQuery query) {
        return noteService.queryNotePage(query);
    }
}
