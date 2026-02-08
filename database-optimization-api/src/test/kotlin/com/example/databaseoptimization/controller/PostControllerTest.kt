package com.example.databaseoptimization.controller

import com.example.databaseoptimization.data.entity.CommentJpaEntity
import com.example.databaseoptimization.data.entity.PostJpaEntity
import com.example.databaseoptimization.data.entity.UserJpaEntity
import com.example.databaseoptimization.repository.CommentRepository
import com.example.databaseoptimization.repository.PostRepository
import com.example.databaseoptimization.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class PostControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var commentRepository: CommentRepository

    private lateinit var savedUsers: List<UserJpaEntity>
    private lateinit var savedPosts: List<PostJpaEntity>

    @BeforeEach
    fun setUp() {
        commentRepository.deleteAll()
        postRepository.deleteAll()
        userRepository.deleteAll()

        savedUsers = (1..3).map { i ->
            userRepository.save(
                UserJpaEntity(name = "User$i", email = "user$i@test.com")
            )
        }

        savedPosts = mutableListOf<PostJpaEntity>().apply {
            savedUsers.forEach { user ->
                (1..5).forEach { j ->
                    add(
                        postRepository.save(
                            PostJpaEntity(
                                title = "Post by ${user.name} #$j",
                                content = "Content $j by ${user.name}",
                                user = user,
                                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
                                    .plusDays((user.id * 10 + j).toLong())
                            )
                        )
                    )
                }
            }
        }

        // 각 Post에 댓글 2개씩
        savedPosts.forEach { post ->
            (1..2).forEach { k ->
                commentRepository.save(
                    CommentJpaEntity(
                        content = "Comment $k on ${post.title}",
                        post = post,
                        user = savedUsers.first()
                    )
                )
            }
        }
    }

    // ===================================================
    //  GET /api/posts - 전체 조회 (페이지네이션)
    // ===================================================

    @Test
    @DisplayName("GET /api/posts - 기본 페이지네이션 (page=0, size=10)")
    fun `get all posts with default pagination`() {
        mockMvc.get("/api/posts")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.content.length()") { value(10) }
                jsonPath("$.totalElements") { value(15) }
                jsonPath("$.totalPages") { value(2) }
                jsonPath("$.number") { value(0) }
                jsonPath("$.content[0].authorName") { exists() }
                jsonPath("$.content[0].title") { exists() }
                jsonPath("$.content[0].commentCount") { exists() }
            }
            .andDo { print() }
    }

    @Test
    @DisplayName("GET /api/posts?page=1&size=5 - 커스텀 페이지네이션")
    fun `get all posts with custom pagination`() {
        mockMvc.get("/api/posts?page=1&size=5")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(5) }
                jsonPath("$.totalElements") { value(15) }
                jsonPath("$.totalPages") { value(3) }
                jsonPath("$.number") { value(1) }
            }
    }

    @Test
    @DisplayName("GET /api/posts - createdAt 내림차순 정렬 확인")
    fun `get all posts sorted by createdAt desc`() {
        mockMvc.get("/api/posts?size=15")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(15) }
                // 기본 정렬이 createdAt DESC이므로 첫 번째가 가장 최신
                jsonPath("$.content[0].title") { exists() }
            }
    }

    // ===================================================
    //  GET /api/posts/{id} - 상세 조회 (댓글 포함)
    // ===================================================

    @Test
    @DisplayName("GET /api/posts/{id} - 상세 조회 (댓글 포함)")
    fun `get post by id with comments`() {
        val postId = savedPosts.first().id

        mockMvc.get("/api/posts/$postId")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.id") { value(postId) }
                jsonPath("$.title") { exists() }
                jsonPath("$.content") { exists() }
                jsonPath("$.authorName") { exists() }
                jsonPath("$.comments.length()") { value(2) }
                jsonPath("$.comments[0].content") { exists() }
                jsonPath("$.comments[0].authorName") { exists() }
            }
    }

    @Test
    @DisplayName("GET /api/posts/{id} - 존재하지 않는 ID (404)")
    fun `get post by non-existent id returns 404`() {
        mockMvc.get("/api/posts/99999")
            .andExpect {
                status { isNotFound() }
            }
    }

    // ===================================================
    //  GET /api/posts/users/{userId} - 사용자별 조회
    // ===================================================

    @Test
    @DisplayName("GET /api/posts/users/{userId} - 사용자별 게시글 조회")
    fun `get posts by user id`() {
        val userId = savedUsers.first().id

        mockMvc.get("/api/posts/users/$userId")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(5) }
                jsonPath("$.totalElements") { value(5) }
                jsonPath("$.totalPages") { value(1) }
            }
    }

    @Test
    @DisplayName("GET /api/posts/users/{userId}?page=0&size=3 - 사용자별 페이지네이션")
    fun `get posts by user id with pagination`() {
        val userId = savedUsers.first().id

        mockMvc.get("/api/posts/users/$userId?page=0&size=3")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.totalElements") { value(5) }
                jsonPath("$.totalPages") { value(2) }
                jsonPath("$.number") { value(0) }
            }
    }
}
