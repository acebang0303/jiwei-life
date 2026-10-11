package com.jiwei.social.controller.admin;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.social.service.NoteService;
import com.jiwei.social.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端内容审核接口
 */
@RestController
@RequestMapping("/admin")
@Slf4j
@Tag(name = "内容审核相关接口")
public class AdminSocialController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private NoteService noteService;

    /**
     * 评价审核列表
     *
     * @param page
     * @param pageSize
     * @param auditStatus
     * @return
     */
    @GetMapping("/review/audit")
    @Operation(summary = "评价审核列表")
    public Result<PageResult> reviewAuditList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer auditStatus) {
        log.info("查询评价审核列表，page: {}, auditStatus: {}", page, auditStatus);
        PageResult pageResult = reviewService.pageQuery(page, pageSize, auditStatus);
        return Result.success(pageResult);
    }

    /**
     * 审核评价
     *
     * @param id
     * @param auditStatus
     * @param auditReason
     * @return
     */
    @PutMapping("/review/{id}/audit")
    @Operation(summary = "审核评价")
    public Result<String> auditReview(@PathVariable Long id,
                                       @RequestParam Integer auditStatus,
                                       @RequestParam(required = false) String auditReason) {
        log.info("评价审核: id={}, status={}, reason={}", id, auditStatus, auditReason);
        reviewService.audit(id, auditStatus, auditReason);
        return Result.success();
    }

    /**
     * 笔记审核列表
     *
     * @param page
     * @param pageSize
     * @param auditStatus
     * @return
     */
    @GetMapping("/note/audit")
    @Operation(summary = "笔记审核列表")
    public Result<PageResult> noteAuditList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer auditStatus) {
        log.info("查询笔记审核列表，page: {}, auditStatus: {}", page, auditStatus);
        PageResult pageResult = noteService.pageAudit(page, pageSize, auditStatus);
        return Result.success(pageResult);
    }

    /**
     * 审核笔记
     *
     * @param id
     * @param auditStatus
     * @return
     */
    @PutMapping("/note/{id}/audit")
    @Operation(summary = "审核笔记")
    public Result<String> auditNote(@PathVariable Long id,
                                     @RequestParam Integer auditStatus,
                                     @RequestParam(required = false) String auditReason) {
        log.info("笔记审核: id={}, status={}, reason={}", id, auditStatus, auditReason);
        noteService.audit(id, auditStatus, auditReason);
        return Result.success();
    }
}
