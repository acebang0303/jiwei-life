package com.jiwei.social.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiwei.auth.mapper.UserMapper;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Comment;
import com.jiwei.pojo.entity.User;
import com.jiwei.social.mapper.CommentMapper;
import com.jiwei.social.service.CommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 * 一级评论：parent_id IS NULL
 * 二级回复：parent_id 指向一级评论ID
 */
@DS("social")
@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Override
    public void publish(Comment comment) {
        //1、设置评论者ID
        comment.setUserId(BaseContext.getCurrentId());

        //2、保存评论
        commentMapper.insert(comment);
        log.info("评论发表成功，commentId: {}, noteId: {}, userId: {}",
                comment.getId(), comment.getNoteId(), comment.getUserId());
    }

    @Override
    public PageResult pageByNoteId(Long noteId, int page, int pageSize) {
        //1、先查一级评论（parent_id IS NULL）
        Page<Comment> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getNoteId, noteId)
               .isNull(Comment::getParentId)
               .orderByDesc(Comment::getCreateTime);

        //2、执行分页查询
        Page<Comment> result = commentMapper.selectPage(p, wrapper);

        //3、批量查询一级评论的二级回复并挂载
        List<Comment> firstLevel = result.getRecords();
        if (!firstLevel.isEmpty()) {
            List<Long> firstIds = firstLevel.stream().map(Comment::getId).collect(Collectors.toList());
            LambdaQueryWrapper<Comment> replyWrapper = new LambdaQueryWrapper<>();
            replyWrapper.eq(Comment::getNoteId, noteId)
                        .in(Comment::getParentId, firstIds)
                        .orderByAsc(Comment::getCreateTime);
            List<Comment> replies = commentMapper.selectList(replyWrapper);
            Map<Long, List<Comment>> replyMap = replies.stream()
                    .collect(Collectors.groupingBy(Comment::getParentId));
            firstLevel.forEach(c -> c.setChildren(replyMap.getOrDefault(c.getId(), new ArrayList<>())));
            //4、统一填充评论者昵称/头像
            List<Comment> all = new ArrayList<>(firstLevel);
            all.addAll(replies);
            fillUserInfo(all);
        }
        return new PageResult(result.getTotal(), result.getRecords());
    }

    /**
     * 批量填充评论者昵称/头像（跨库查 user 表，无事务方法内可安全切换）
     */
    private void fillUserInfo(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        List<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        comments.forEach(c -> {
            User u = userMap.get(c.getUserId());
            if (u != null) {
                c.setNickname(u.getNickname());
                c.setAvatar(u.getAvatar());
            }
        });
    }

    @Override
    public void deleteById(Long commentId) {
        //1、归属校验：只能删除自己的评论
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new com.jiwei.common.exception.BaseException("评论不存在");
        }
        if (!comment.getUserId().equals(BaseContext.getCurrentId())) {
            throw new com.jiwei.common.exception.BaseException("无权删除该评论");
        }
        //2、删除评论
        commentMapper.deleteById(commentId);
        log.info("评论删除，commentId: {}", commentId);
    }
}
