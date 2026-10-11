package com.jiwei.social.controller.user;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.Comment;
import com.jiwei.social.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * C端评论接口
 * 支持一级评论和二级回复
 */
@RestController
@RequestMapping("/user/comment")
@Slf4j
@Tag(name = "用户评论相关接口")
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     * 发表评论
     *
     * @param comment
     * @return
     */
    @PostMapping
    @Operation(summary = "发表评论")
    public Result<String> publish(@RequestBody Comment comment) {
        log.info("发表评论，noteId: {}", comment.getNoteId());
        commentService.publish(comment);
        return Result.success();
    }

    /**
     * 查询笔记的评论列表
     *
     * @param noteId
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/note/{noteId}")
    @Operation(summary = "笔记评论列表")
    public Result<PageResult> listByNoteId(
            @PathVariable Long noteId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("查询评论，noteId: {}, page: {}", noteId, page);
        PageResult pageResult = commentService.pageByNoteId(noteId, page, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 删除评论
     *
     * @param commentId
     * @return
     */
    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除评论")
    public Result<String> delete(@PathVariable Long commentId) {
        log.info("删除评论，commentId: {}", commentId);
        commentService.deleteById(commentId);
        return Result.success();
    }
}
