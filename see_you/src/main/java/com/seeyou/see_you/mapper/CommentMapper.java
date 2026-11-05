package com.seeyou.see_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.entity.Comment;
import com.seeyou.see_you.vo.CommentVO;
import com.seeyou.see_you.vo.MyCommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Select({"<script>",
            "SELECT c.comment_id, c.user_id, u.nickname, u.avatar, ",
            "c.resource_type, c.resource_id, c.comment_text, c.status, c.create_time ",
            "FROM psy_comment c LEFT JOIN sys_user u ON c.user_id = u.user_id ",
        "WHERE c.resource_type = #{resourceType} AND c.resource_id = #{resourceId} ",
        "AND ( c.status = 1 ",
        // 未通过的评论（待审核 / 已拒绝）只对作者本人可见，让作者明确知道每条评论的审核结果
        "<if test='viewerId != null'>OR (c.status IN (0, -1) AND c.user_id = #{viewerId})</if>",
        ") ",
        "ORDER BY c.create_time DESC",
        "</script>"})
    Page<CommentVO> selectCommentWithUserPage(Page<CommentVO> page,
                                              @Param("resourceType") Integer resourceType,
                                              @Param("resourceId") Long resourceId,
                                              @Param("viewerId") Long viewerId);

    @Select({"<script>",
            "SELECT c.comment_id, c.user_id, u.nickname, u.avatar, ",
            "c.resource_type, c.resource_id, c.comment_text, c.status, c.create_time ",
            "FROM psy_comment c LEFT JOIN sys_user u ON c.user_id = u.user_id ",
            "<where>",
            "<if test='status != null'>AND c.status = #{status}</if>",
            "<if test='keyword != null and keyword != \"\"'>AND (c.comment_text LIKE CONCAT('%', #{keyword}, '%') OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))</if>",
            "</where>",
            "ORDER BY c.create_time DESC",
            "</script>"})
    Page<CommentVO> selectAllCommentsWithUserPage(Page<CommentVO> page,
                                                   @Param("status") Integer status,
                                                   @Param("keyword") String keyword);

    /**
     * 我的评论：跨音频 / 视频聚合，带资源名与封面。
     * userId 由 Controller 从 token 取，这里只做参数化查询，避免越权。
     */
    @Select({"<script>",
            "SELECT c.comment_id, c.resource_type, c.resource_id, c.comment_text, c.status, c.create_time, ",
            "CASE c.resource_type WHEN 1 THEN a.audio_name ELSE v.video_name END AS resource_name, ",
            "CASE c.resource_type WHEN 1 THEN a.cover_img ELSE v.cover_img END AS resource_cover ",
            "FROM psy_comment c ",
            "LEFT JOIN psy_audio a ON c.resource_type = 1 AND c.resource_id = a.audio_id ",
            "LEFT JOIN psy_video v ON c.resource_type = 2 AND c.resource_id = v.video_id ",
            "WHERE c.user_id = #{userId} ",
            "<if test='status != null'>AND c.status = #{status}</if>",
            "ORDER BY c.create_time DESC",
            "</script>"})
    Page<MyCommentVO> selectMyComments(Page<MyCommentVO> page,
                                       @Param("userId") Long userId,
                                       @Param("status") Integer status);
}
