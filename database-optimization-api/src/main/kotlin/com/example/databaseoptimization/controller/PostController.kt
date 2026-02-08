package com.example.databaseoptimization.controller

import com.example.databaseoptimization.controller.dto.PostDetailResponse
import com.example.databaseoptimization.controller.dto.PostResponse
import com.example.databaseoptimization.repository.PostRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts")
class PostController(
    private val postRepository: PostRepository
) {

    // GET /api/posts - 전체 조회 (페이지네이션)
    @GetMapping
    fun getAllPosts(
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable
    ): Page<PostResponse> {
        return postRepository.findAllWithUser(pageable)
            .map { PostResponse.from(it) }
    }

    // GET /api/posts/{id} - 상세 조회 (댓글 포함)
    @GetMapping("/{id}")
    fun getPostById(@PathVariable id: Long): ResponseEntity<PostDetailResponse> {
        val post = postRepository.findByIdWithUserAndComments(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(PostDetailResponse.from(post))
    }

    // GET /api/users/{userId}/posts - 사용자별 조회
    @GetMapping("/users/{userId}")
    fun getPostsByUserId(
        @PathVariable userId: Long,
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable
    ): Page<PostResponse> {
        return postRepository.findByUserId(userId, pageable)
            .map { PostResponse.from(it) }
    }
}
