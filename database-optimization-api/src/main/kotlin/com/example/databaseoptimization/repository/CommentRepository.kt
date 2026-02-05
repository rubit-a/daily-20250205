package com.example.databaseoptimization.repository

import com.example.databaseoptimization.data.entity.CommentJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CommentRepository : JpaRepository<CommentJpaEntity, Long> {

    // 게시글별 댓글 조회
    fun findByPostId(postId: Long): List<CommentJpaEntity>

    // 사용자별 댓글 조회
    fun findByUserId(userId: Long): List<CommentJpaEntity>

    // N+1 해결용: Fetch Join으로 User 함께 조회
    @Query("SELECT c FROM CommentJpaEntity c JOIN FETCH c.user WHERE c.post.id = :postId")
    fun findByPostIdWithUser(postId: Long): List<CommentJpaEntity>
}
