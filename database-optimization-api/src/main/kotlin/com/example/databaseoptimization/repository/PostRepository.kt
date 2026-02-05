package com.example.databaseoptimization.repository

import com.example.databaseoptimization.data.entity.PostJpaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface PostRepository : JpaRepository<PostJpaEntity, Long> {

    // 사용자별 게시글 조회 (idx_post_user_created 인덱스 활용)
    fun findByUserId(userId: Long): List<PostJpaEntity>

    // 사용자별 게시글 조회 + 페이지네이션
    fun findByUserId(userId: Long, pageable: Pageable): Page<PostJpaEntity>

    // 기간별 조회 (idx_post_created_at 인덱스 활용)
    fun findByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<PostJpaEntity>

    // 기간별 조회 + 페이지네이션
    fun findByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<PostJpaEntity>

    // N+1 해결용: Fetch Join으로 User 함께 조회
    @Query("SELECT p FROM PostJpaEntity p JOIN FETCH p.user")
    fun findAllWithUser(): List<PostJpaEntity>

    // N+1 해결용: Fetch Join + 페이지네이션 (countQuery 필요)
    @Query(
        value = "SELECT p FROM PostJpaEntity p JOIN FETCH p.user",
        countQuery = "SELECT COUNT(p) FROM PostJpaEntity p"
    )
    fun findAllWithUser(pageable: Pageable): Page<PostJpaEntity>

    // N+1 해결용: Post + User + Comments 모두 조회
    @Query("SELECT DISTINCT p FROM PostJpaEntity p JOIN FETCH p.user LEFT JOIN FETCH p.comments")
    fun findAllWithUserAndComments(): List<PostJpaEntity>

    // 단건 조회 + User + Comments
    @Query("SELECT p FROM PostJpaEntity p JOIN FETCH p.user LEFT JOIN FETCH p.comments WHERE p.id = :id")
    fun findByIdWithUserAndComments(id: Long): PostJpaEntity?
}
