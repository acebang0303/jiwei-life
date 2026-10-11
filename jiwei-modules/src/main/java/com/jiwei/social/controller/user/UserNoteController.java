package com.jiwei.social.controller.user;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.Comment;
import com.jiwei.pojo.entity.Note;
import com.jiwei.social.service.CommentService;
import com.jiwei.social.service.FollowService;
import com.jiwei.social.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C端笔记与评论接口
 */
@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "用户端笔记社区相关接口")
public class UserNoteController {

    @Autowired
    private NoteService noteService;

    @Autowired
    private FollowService followService;

    @Autowired
    private CommentService commentService;

    /**
     * 发布笔记
     *
     * @param note
     * @return
     */
    @PostMapping("/note")
    @Operation(summary = "发布笔记")
    public Result<String> publish(@RequestBody Note note) {
        noteService.publish(note);
        return Result.success();
    }

    /**
     * 首页推荐Feed流
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/note/feed")
    @Operation(summary = "首页推荐Feed流")
    public Result<PageResult> feed(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(noteService.feed(page, pageSize));
    }

    /**
     * 热门笔记排行榜
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/note/hot")
    @Operation(summary = "热门笔记排行榜")
    public Result<PageResult> hot(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(noteService.hotRanking(page, pageSize));
    }

    /**
     * 点赞/取消赞
     *
     * @param id
     * @return
     */
    @PostMapping("/note/{id}/like")
    @Operation(summary = "点赞/取消赞")
    public Result<Map<String, Boolean>> like(@PathVariable Long id) {
        boolean liked = noteService.like(id);
        Map<String, Boolean> result = new HashMap<>();
        result.put("liked", liked);
        return Result.success(result);
    }

    /**
     * 收藏/取消收藏
     *
     * @param id
     * @return
     */
    @PostMapping("/note/{id}/collect")
    @Operation(summary = "收藏/取消收藏")
    public Result<Map<String, Boolean>> collect(@PathVariable Long id) {
        boolean collected = noteService.collect(id);
        Map<String, Boolean> result = new HashMap<>();
        result.put("collected", collected);
        return Result.success(result);
    }

    /**
     * 关注/取关用户
     *
     * @param userId
     * @return
     */
    @PostMapping("/follow/{userId}")
    @Operation(summary = "关注/取关用户")
    public Result<Map<String, Boolean>> follow(@PathVariable Long userId) {
        boolean following = followService.follow(userId);
        Map<String, Boolean> result = new HashMap<>();
        result.put("following", following);
        return Result.success(result);
    }

    /**
     * 我关注的用户列表
     */
    @GetMapping("/follow/me")
    @Operation(summary = "我关注的用户列表")
    public Result<List<Map<String, Object>>> myFollowing() {
        return Result.success(followService.listFollowing());
    }

    /**
     * 笔记详情（含作者信息、互动状态、评论列表）
     */
    @GetMapping("/note/{id}")
    @Operation(summary = "笔记详情")
    public Result<Note> detail(@PathVariable Long id) {
        return Result.success(noteService.detail(id));
    }

    /**
     * 我的笔记
     */
    @GetMapping("/note/my")
    @Operation(summary = "我的笔记")
    public Result<PageResult> myNotes(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(noteService.myNotes(page, pageSize));
    }
}
