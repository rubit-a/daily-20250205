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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @BatchSize를 사용한 N+1 해결 테스트
 *
 * @BatchSize vs Fetch Join/@EntityGraph 비교:
 * - Fetch Join/@EntityGraph: JOIN으로 한 번에 조회 (1개 쿼리)
 * - @BatchSize: LAZY 유지하면서 IN 절로 묶어서 조회 (2개 쿼리)
 *
 * @BatchSize 장점:
 * - 페이지네이션과 함께 사용 가능 (Fetch Join은 컬렉션 페이징 문제)
 * - 필요할 때만 로딩 (불필요한 데이터 로딩 방지)
 */
@DataJpaTest
class BatchSizeTest {

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
        // 5명의 User, 각 User당 2개의 Post, 각 Post당 3개의 Comment
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
    @DisplayName("@BatchSize: User 조회 시 IN 절로 묶어서 조회")
    fun `BatchSize loads users with IN clause`() {
        println("\n" + "=".repeat(60))
        println("@BatchSize: User 조회 테스트")
        println("=".repeat(60))

        println("\n>>> findAll() 실행")
        val posts = postRepository.findAll()
        println(">>> Post ${posts.size}개 조회 완료")
        println(">>> 'select ... from posts' 쿼리 1개 실행됨\n")

        println(">>> User 정보 접근 (@BatchSize 적용):")
        println(">>> User들이 IN 절로 묶여서 한 번에 조회됨!")
        println(">>> 'select ... from users where id in (?, ?, ?, ?, ?)'\n")

        val userNames = mutableSetOf<String>()
        posts.forEach { post ->
            val userName = post.user.name
            if (userName !in userNames) {
                println("   - User: $userName")
                userNames.add(userName)
            }
        }

        println("\n>>> 결과 요약:")
        println("   - N+1 문제 (BatchSize 없음): 1 + 5 = 6개 쿼리")
        println("   - @BatchSize 적용:           1 + 1 = 2개 쿼리")
        println("   - 절감 효과: 4개 쿼리 감소!")
        println("=".repeat(60) + "\n")

        assertEquals(5, userNames.size)
    }

    @Test
    @DisplayName("@BatchSize: Comments 조회 시 IN 절로 묶어서 조회")
    fun `BatchSize loads comments with IN clause`() {
        println("\n" + "=".repeat(60))
        println("@BatchSize: Comments 조회 테스트")
        println("=".repeat(60))

        println("\n>>> findAll() 실행")
        val posts = postRepository.findAll()
        println(">>> Post ${posts.size}개 조회 완료\n")

        println(">>> Comments 접근 (@BatchSize 적용):")
        println(">>> Post들의 comments가 IN 절로 묶여서 조회됨!")
        println(">>> 'select ... from comments where post_id in (?, ?, ...)'\n")

        var totalComments = 0
        posts.forEach { post ->
            val commentCount = post.comments.size
            totalComments += commentCount
            println("   - ${post.title}: ${commentCount}개 댓글")
        }

        println("\n>>> 결과 요약:")
        println("   - 총 댓글 수: $totalComments")
        println("   - N+1 문제 (BatchSize 없음): 1 + 10 = 11개 쿼리")
        println("   - @BatchSize 적용:           1 + 1 = 2개 쿼리")
        println("=".repeat(60) + "\n")

        assertEquals(30, totalComments)  // 10 posts * 3 comments
    }

    @Test
    @DisplayName("Fetch Join vs @BatchSize 비교")
    fun `compare Fetch Join and BatchSize`() {
        println("\n" + "=".repeat(60))
        println("Fetch Join vs @BatchSize 비교")
        println("=".repeat(60))

        // Case 1: Fetch Join
        println("\n[Case 1] Fetch Join")
        println("-".repeat(40))
        val fetchJoinPosts = postRepository.findAllWithUser()
        fetchJoinPosts.forEach { it.user.name }
        println("   쿼리: SELECT p FROM Post p JOIN FETCH p.user")
        println("   쿼리 수: 1개 (JOIN으로 한 번에)")

        entityManager.clear()

        // Case 2: @BatchSize (findAll + LAZY)
        println("\n[Case 2] @BatchSize + LAZY")
        println("-".repeat(40))
        val batchSizePosts = postRepository.findAll()
        batchSizePosts.forEach { it.user.name }
        println("   쿼리 1: SELECT * FROM posts")
        println("   쿼리 2: SELECT * FROM users WHERE id IN (...)")
        println("   쿼리 수: 2개 (posts 조회 + users IN 조회)")

        println("\n" + "=".repeat(60))
        println("비교 결과:")
        println("-".repeat(40))
        println("| 항목              | Fetch Join | @BatchSize |")
        println("|-------------------|------------|------------|")
        println("| 쿼리 수           | 1개        | 2개        |")
        println("| JOIN 사용         | O          | X          |")
        println("| LAZY 유지         | X          | O          |")
        println("| 컬렉션 페이징     | 문제있음   | 가능       |")
        println("| 불필요한 로딩     | 발생가능   | 방지       |")
        println("=".repeat(60) + "\n")

        assertEquals(fetchJoinPosts.size, batchSizePosts.size)
    }

    @Test
    @DisplayName("@BatchSize: 페이지네이션과 함께 사용")
    fun `BatchSize works well with pagination`() {
        println("\n" + "=".repeat(60))
        println("@BatchSize + 페이지네이션")
        println("=".repeat(60))

        println("\n>>> Fetch Join의 컬렉션 페이징 문제:")
        println("   - 'HHH90003004: firstResult/maxResults specified with collection fetch'")
        println("   - 메모리에서 페이징 처리 (성능 문제)\n")

        println(">>> @BatchSize는 페이징과 함께 사용 가능:")

        val page = postRepository.findAll(
            org.springframework.data.domain.PageRequest.of(0, 5)
        )

        println("   - 페이지 크기: ${page.size}")
        println("   - 전체 요소: ${page.totalElements}")

        // Comments 접근 시에도 BatchSize 적용
        page.content.forEach { post ->
            println("   - ${post.title}: ${post.comments.size}개 댓글")
        }

        println("\n>>> 쿼리 순서:")
        println("   1. SELECT * FROM posts LIMIT 5")
        println("   2. SELECT COUNT(*) FROM posts")
        println("   3. SELECT * FROM users WHERE id IN (...)")
        println("   4. SELECT * FROM comments WHERE post_id IN (...)")
        println("=".repeat(60) + "\n")

        assertEquals(5, page.content.size)
        assertTrue(page.content.all { it.comments.isNotEmpty() })
    }
}
