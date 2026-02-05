package com.example.databaseoptimization.data.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "posts",
    indexes = [
        Index(name = "idx_post_created_at", columnList = "created_at"),
        Index(name = "idx_post_user_created", columnList = "user_id, created_at")
    ]
)
class PostJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserJpaEntity,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    val comments: MutableList<CommentJpaEntity> = mutableListOf()
)
