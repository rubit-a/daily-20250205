package com.example.databaseoptimization.controller.dto

import com.example.databaseoptimization.data.entity.CommentJpaEntity
import com.example.databaseoptimization.data.entity.PostJpaEntity
import java.time.LocalDateTime

data class PostResponse(
    val id: Long,
    val title: String,
    val content: String,
    val authorName: String,
    val createdAt: LocalDateTime,
    val commentCount: Int
) {
    companion object {
        fun from(post: PostJpaEntity): PostResponse = PostResponse(
            id = post.id,
            title = post.title,
            content = post.content,
            authorName = post.user.name,
            createdAt = post.createdAt,
            commentCount = post.comments.size
        )
    }
}

data class PostDetailResponse(
    val id: Long,
    val title: String,
    val content: String,
    val authorName: String,
    val createdAt: LocalDateTime,
    val comments: List<CommentResponse>
) {
    companion object {
        fun from(post: PostJpaEntity): PostDetailResponse = PostDetailResponse(
            id = post.id,
            title = post.title,
            content = post.content,
            authorName = post.user.name,
            createdAt = post.createdAt,
            comments = post.comments.map { CommentResponse.from(it) }
        )
    }
}

data class CommentResponse(
    val id: Long,
    val content: String,
    val authorName: String,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(comment: CommentJpaEntity): CommentResponse = CommentResponse(
            id = comment.id,
            content = comment.content,
            authorName = comment.user.name,
            createdAt = comment.createdAt
        )
    }
}
