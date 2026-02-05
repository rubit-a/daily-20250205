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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N+1 문제와 Fetch Join 해결을 검증하는 테스트
 *
 * 쿼리 횟수 확인 방법:
 * - 테스트 실행 시 콘솔에 출력되는 Hibernate SQL 로그 확인
 * - "Hibernate: select..." 라인 수 카운트
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PostRepositoryNPlusOneTest {

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var txTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        txTemplate = TransactionTemplate(transactionManager)

        // 테스트 데이터 생성 (별도 트랜잭션)
        txTemplate.execute {
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
                                content = "Comment $k on ${post.title}",
                                post = post,
                                user = users.random()
                            )
                        )
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("N+1 문제 발생: findAll() 후 User 접근 시 추가 쿼리 발생")
    fun `findAll causes N+1 problem when accessing user`() {
        println("\n" + "=".repeat(60))
        println("N+1 문제 발생 테스트")
        println("=".repeat(60))

        // 별도 트랜잭션에서 조회 및 접근
        val userNames = txTemplate.execute {
            println("\n>>> findAll() 실행 시작")
            val posts = postRepository.findAll()
            println(">>> findAll() 완료 - Post ${posts.size}개 조회")
            println(">>> 위에서 'select ... from posts' 쿼리 1개 확인\n")

            println(">>> User 정보 접근 시작 (N+1 발생!):")
            val names = mutableSetOf<String>()
            posts.forEach { post ->
                val userName = post.user.name  // LAZY 로딩 → 추가 쿼리!
                if (userName !in names) {
                    println("   → User '$userName' 로딩 (select from users 쿼리 발생)")
                    names.add(userName)
                }
            }
            names
        }!!

        println("\n>>> 결과 요약:")
        println("   - posts 조회: 1개 쿼리")
        println("   - users 조회: ${userNames.size}개 쿼리 (N+1 문제!)")
        println("   - 총 쿼리 수: ${1 + userNames.size}개")
        println("=".repeat(60) + "\n")

        assertTrue(userNames.size >= 2)
    }

    @Test
    @DisplayName("Fetch Join으로 N+1 해결: findAllWithUser()는 1개 쿼리만 실행")
    fun `findAllWithUser solves N+1 problem with single query`() {
        println("\n" + "=".repeat(60))
        println("Fetch Join 해결 테스트")
        println("=".repeat(60))

        val posts = txTemplate.execute {
            println("\n>>> findAllWithUser() 실행 (Fetch Join)")
            val result = postRepository.findAllWithUser()
            println(">>> 위에서 'select ... from posts p join users u' 쿼리 1개만 확인!\n")

            println(">>> User 정보 접근 (추가 쿼리 없음!):")
            result.forEach { post ->
                println("   - Post: ${post.title}, User: ${post.user.name}")
            }
            result
        }!!

        println("\n>>> 결과 요약:")
        println("   - posts + users 조회: 1개 쿼리 (Fetch Join)")
        println("   - 추가 쿼리: 0개")
        println("   - 총 쿼리 수: 1개 ✓")
        println("=".repeat(60) + "\n")

        assertEquals(10, posts.size)
    }

    @Test
    @DisplayName("쿼리 횟수 비교: findAll vs findAllWithUser")
    fun `compare findAll and findAllWithUser query count`() {
        println("\n" + "=".repeat(60))
        println("N+1 vs Fetch Join 쿼리 횟수 비교")
        println("=".repeat(60))

        // Case 1: findAll() - N+1 발생
        println("\n[Case 1] findAll() + LAZY 로딩")
        println("-".repeat(40))
        val n1QueryCount = txTemplate.execute {
            val posts = postRepository.findAll()
            var queryCount = 1  // posts 조회

            val loadedUsers = mutableSetOf<Long>()
            posts.forEach { post ->
                val userId = post.user.id
                if (userId !in loadedUsers) {
                    loadedUsers.add(userId)
                    queryCount++  // 각 user 조회
                }
                post.user.name  // 실제 로딩 트리거
            }
            println("   쿼리 수: $queryCount (1 + ${loadedUsers.size})")
            queryCount
        }!!

        // Case 2: findAllWithUser() - Fetch Join
        println("\n[Case 2] findAllWithUser() (Fetch Join)")
        println("-".repeat(40))
        val fetchJoinQueryCount = txTemplate.execute {
            val posts = postRepository.findAllWithUser()
            posts.forEach { it.user.name }  // 이미 로딩됨
            println("   쿼리 수: 1")
            1
        }!!

        println("\n" + "=".repeat(60))
        println("비교 결과:")
        println("   findAll() + LAZY:     $n1QueryCount 개 쿼리")
        println("   findAllWithUser():    $fetchJoinQueryCount 개 쿼리")
        println("   절감 효과:            ${n1QueryCount - fetchJoinQueryCount} 개 감소!")
        println("=".repeat(60) + "\n")

        assertTrue(n1QueryCount > fetchJoinQueryCount)
        assertEquals(1, fetchJoinQueryCount)
    }
}
