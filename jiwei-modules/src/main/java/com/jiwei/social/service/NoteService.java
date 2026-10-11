package com.jiwei.social.service;

import com.jiwei.pojo.entity.Note;
import com.jiwei.common.result.PageResult;

/**
 * 笔记服务接口
 * Feed流采用推拉结合模式
 */
public interface NoteService {

    /**
     * 发布笔记（推拉结合）
     * - 大V (>10000粉): 只写自己时间线，粉丝拉取
     * - 普通用户: 写入粉丝Feed收件箱（Redis ZSet）
     */
    void publish(Note note);

    /**
     * 首页推荐Feed流
     */
    PageResult feed(int page, int pageSize);

    /**
     * 热门笔记排行榜（Redis ZSet）
     * Score = likeCount*2 + commentCount*3 + collectCount*5
     */
    PageResult hotRanking(int page, int pageSize);

    /**
     * 点赞/取消赞
     */
    boolean like(Long noteId);

    /**
     * 收藏/取消收藏
     */
    boolean collect(Long noteId);

    /**
     * 笔记详情（含作者信息、点赞收藏状态、评论列表）
     */
    Note detail(Long id);

    /**
     * 我的笔记列表
     */
    PageResult myNotes(int page, int pageSize);

    /**
     * 审核列表（管理端，按审核状态分页）
     */
    PageResult pageAudit(int page, int pageSize, Integer auditStatus);

    /**
     * 审核笔记（通过/驳回）
     */
    void audit(Long id, Integer auditStatus, String auditReason);
}
