package com.example.databaseoptimization.repository

import com.example.databaseoptimization.data.entity.CommentJpaEntity
import com.example.databaseoptimization.data.entity.PostJpaEntity
import com.example.databaseoptimization.data.entity.UserJpaEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * @EntityGraph를 사용한 N+1 해결 테스트
 *
 * @EntityGraph vs Fetch Join 비교:
 * - Fetch Join: JPQL 쿼리에 직접 JOIN FETCH 작성
 * - @EntityGraph: 어노테이션으로 선언적 사용, 쿼리 메서드와 분리
 */
@DataJpaTest
class EntityGraphTest {

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUp() {
        val users = (1..5).map { i ->
            userRepository.save(
                UserJpaEntity(
                    name = "User$i",
                    email = "user$i@test.com"
                )
            )
        }

        users.forEach { user ->
            (1..2).forEach { j ->
                val post = postRepository.save(
                    PostJpaEntity(
                        title = "Post by ${user.name} - $j",
                        content = "Content $j",
                        user = user
                    )
                )
                (1..3).forEach { k ->
                    commentRepository.save(
                        CommentJpaEntity(
                            content = "Comment $k",
                            post = post,
                            user = users.random()
                        )
                    )
                }
            }
        }

        entityManager.flush()
        entityManager.clear()
    }

    @Test
    @DisplayName("@EntityGraph: User 함께 조회 - N+1 해결")
    fun `EntityGraph loads user with single query`() {
        println("\n" + "=".repeat(60))
        println("@EntityGraph: User 함께 조회 테스트")
        println("=".repeat(60))

        println("\n>>> findAllByOrderByCreatedAtDesc() 실행")
        println(">>> @EntityGraph(attributePaths = [\"user\"]) 적용")
        val posts = postRepository.findAllByOrderByCreatedAtDesc()

        println("\n>>> User 정보 접근 (추가 쿼리 없음!):")
        posts.take(5).forEach { post ->
            println("   - Post: ${post.title}, User: ${post.user.name}")
        }

        println("\n>>> 결과: LEFT JOIN으로 1개 쿼리만 실행!")
        println("=".repeat(60) + "\n")

        assertEquals(10, posts.size)
    }

    @Test
    @DisplayName("@EntityGraph: User + Comments 함께 조회")
    fun `EntityGraph loads user and comments`() {
        println("\n" + "=".repeat(60))
        println("@EntityGraph: User + Comments 함께 조회 테스트")
        println("=".repeat(60))

        val postId = postRepository.findAll().first().id
        entityManager.clear()

        println("\n>>> findWithUserAndCommentsById($postId) 실행")
        println(">>> @EntityGraph(attributePaths = [\"user\", \"comments\"]) 적용")
        val post = postRepository.findWithUserAndCommentsById(postId)

        assertNotNull(post)
        println("\n>>> Post: ${post.title}")
        println(">>> User: ${post.user.name}")
        println(">>> Comments (${post.comments.size}개):")
        post.comments.forEach { comment ->
            println("   - ${comment.content}")
        }

        println("\n>>> 결과: 1개 쿼리로 Post + User + Comments 모두 로드!")
        println("=".repeat(60) + "\n")

        assertTrue(post.comments.isNotEmpty())
    }

    @Test
    @DisplayName("@EntityGraph + 페이지네이션")
    fun `EntityGraph works with pagination`() {
        println("\n" + "=".repeat(60))
        println("@EntityGraph + 페이지네이션 테스트")
        println("=".repeat(60))

        println("\n>>> findAllByOrderByIdDesc(PageRequest.of(0, 5)) 실행")
        println(">>> @EntityGraph(attributePaths = [\"user\"]) + Pageable")
        val page = postRepository.findAllByOrderByIdDesc(PageRequest.of(0, 5))

        println("\n>>> 페이지 정보:")
        println("   - 페이지 크기: ${page.size}")
        println("   - 전체 요소: ${page.totalElements}")
        println("   - 전체 페이지: ${page.totalPages}")

        println("\n>>> 내용:")
        page.content.forEach { post ->
            println("   - Post: ${post.title}, User: ${post.user.name}")
        }

        println("\n>>> 결과: 데이터 쿼리 + COUNT 쿼리 = 2개")
        println("=".repeat(60) + "\n")

        assertEquals(5, page.content.size)
        assertEquals(10, page.totalElements)
    }

    @Test
    @DisplayName("Fetch Join vs @EntityGraph 비교")
    fun `compare Fetch Join and EntityGraph`() {
        println("\n" + "=".repeat(60))
        println("Fetch Join vs @EntityGraph 비교")
        println("=".repeat(60))

        // Case 1: Fetch Join
        println("\n[Case 1] Fetch Join (@Query + JOIN FETCH)")
        println("-".repeat(40))
        val fetchJoinResult = postRepository.findAllWithUser()
        fetchJoinResult.forEach { it.user.name }
        println("   메서드: findAllWithUser()")
        println("   쿼리: SELECT p FROM Post p JOIN FETCH p.user")

        entityManager.clear()

        // Case 2: @EntityGraph
        println("\n[Case 2] @EntityGraph")
        println("-".repeat(40))
        val entityGraphResult = postRepository.findAllByOrderByCreatedAtDesc()
        entityGraphResult.forEach { it.user.name }
        println("   메서드: findAllByOrderByCreatedAtDesc()")
        println("   어노테이션: @EntityGraph(attributePaths = [\"user\"])")

        println("\n" + "=".repeat(60))
        println("비교 결과:")
        println("-".repeat(40))
        println("| 항목          | Fetch Join    | @EntityGraph  |")
        println("|---------------|---------------|---------------|")
        println("| 쿼리 수       | 1개           | 1개           |")
        println("| 작성 방식     | JPQL 직접작성 | 어노테이션     |")
        println("| JOIN 타입     | INNER JOIN    | LEFT JOIN     |")
        println("| 재사용성      | 쿼리에 종속   | 메서드에 적용  |")
        println("=".repeat(60) + "\n")

        assertEquals(fetchJoinResult.size, entityGraphResult.size)
    }
}
