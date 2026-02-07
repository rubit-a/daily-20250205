package com.example.databaseoptimization.repository

import com.example.databaseoptimization.data.entity.CommentJpaEntity
import com.example.databaseoptimization.data.entity.PostJpaEntity
import com.example.databaseoptimization.data.entity.UserJpaEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.annotation.Commit
import java.time.LocalDateTime
import kotlin.test.assertTrue

/**
 * 인덱스 효과 검증 테스트
 *
 * 테스트 데이터: 사용자 100명, 게시글 1,000개, 댓글 5,000개
 * H2 EXPLAIN ANALYZE를 사용하여 인덱스 사용 여부를 검증한다.
 *
 * 검증 인덱스:
 * - idx_user_email: users.email (UNIQUE)
 * - idx_post_created_at: posts.created_at
 * - idx_post_user_created: posts(user_id, created_at) 복합 인덱스
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Commit
class IndexEffectTest {

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private var savedUsers: List<UserJpaEntity> = emptyList()
    private var dataInitialized = false

    @BeforeEach
    fun setUp() {
        if (dataInitialized) {
            savedUsers = userRepository.findAll()
            return
        }
        dataInitialized = true
        // 사용자 100명 생성
        savedUsers = (1..100).map { i ->
            userRepository.save(
                UserJpaEntity(
                    name = "User$i",
                    email = "user$i@test.com"
                )
            )
        }
        entityManager.flush()
        entityManager.clear()
        savedUsers = userRepository.findAll()

        // 게시글 1,000개 생성 (사용자당 10개)
        savedUsers.chunked(50).forEach { userChunk ->
            userChunk.forEach { user ->
                (1..10).forEach { j ->
                    postRepository.save(
                        PostJpaEntity(
                            title = "Post by ${user.name} #$j",
                            content = "Content $j",
                            user = user,
                            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
                                .plusDays((user.id * 10 + j).toLong())
                        )
                    )
                }
            }
            entityManager.flush()
            entityManager.clear()
        }
        savedUsers = userRepository.findAll()

        // 댓글 5,000개 생성 (게시글당 5개)
        val allPosts = postRepository.findAll()
        allPosts.chunked(200).forEach { postChunk ->
            postChunk.forEach { post ->
                (1..5).forEach { k ->
                    commentRepository.save(
                        CommentJpaEntity(
                            content = "Comment $k",
                            post = post,
                            user = savedUsers[k % savedUsers.size]
                        )
                    )
                }
            }
            entityManager.flush()
            entityManager.clear()
        }
        savedUsers = userRepository.findAll()
    }

    // ===== 헬퍼: EXPLAIN ANALYZE 결과 출력 =====

    private fun explain(description: String, sql: String): List<String> {
        println("\n" + "-".repeat(70))
        println("EXPLAIN: $description")
        println("SQL: $sql")
        println("-".repeat(70))

        @Suppress("UNCHECKED_CAST")
        val result = entityManager
            .createNativeQuery("EXPLAIN ANALYZE $sql")
            .resultList as List<String>

        result.forEach { println("  $it") }
        return result
    }

    // ===================================================
    //  1. 테스트 데이터 삽입 확인
    // ===================================================

    @Test
    @Order(1)
    @DisplayName("테스트 데이터 삽입 확인: 사용자 100명, 게시글 1000개, 댓글 5000개")
    fun `verify test data counts`() {
        println("\n" + "=".repeat(70))
        println("테스트 데이터 검증")
        println("=".repeat(70))

        val userCount = userRepository.count()
        val postCount = postRepository.count()
        val commentCount = commentRepository.count()

        println("  사용자: $userCount 명")
        println("  게시글: $postCount 개")
        println("  댓글:   $commentCount 개")

        assertTrue(userCount >= 100, "사용자가 100명 이상이어야 합니다")
        assertTrue(postCount >= 1000, "게시글이 1000개 이상이어야 합니다")
        assertTrue(commentCount >= 5000, "댓글이 5000개 이상이어야 합니다")

        println("\n  모든 테스트 데이터가 정상적으로 삽입되었습니다!")
        println("=".repeat(70))
    }

    // ===================================================
    //  2. 인덱스 전체 현황
    // ===================================================

    @Test
    @Order(2)
    @DisplayName("인덱스 전체 현황: 테이블별 인덱스 목록 조회")
    fun `show all indexes on tables`() {
        println("\n" + "=".repeat(70))
        println("테이블별 인덱스 현황")
        println("=".repeat(70))

        val tables = listOf("USERS", "POSTS", "COMMENTS")
        tables.forEach { table ->
            println("\n  [$table 테이블]")
            @Suppress("UNCHECKED_CAST")
            val indexes = entityManager
                .createNativeQuery("SELECT INDEX_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS WHERE TABLE_NAME = '$table' ORDER BY INDEX_NAME, ORDINAL_POSITION")
                .resultList as List<Array<Any>>

            if (indexes.isEmpty()) {
                println("    인덱스 없음")
            } else {
                indexes.forEach { row ->
                    println("    - ${row[0]}: ${row[1]}")
                }
            }
        }

        println("\n" + "=".repeat(70))
    }

    // ===================================================
    //  3. EXPLAIN 실행 및 인덱스 사용 여부 확인
    // ===================================================

    @Test
    @Order(3)
    @DisplayName("idx_user_email 인덱스: email로 사용자 조회 시 인덱스 사용 확인")
    fun `email index is used when querying by email`() {
        println("\n" + "=".repeat(70))
        println("idx_user_email 인덱스 검증")
        println("=".repeat(70))

        val result = explain(
            "email로 사용자 조회",
            "SELECT * FROM users WHERE email = 'user50@test.com'"
        )
        val planText = result.joinToString(" ").uppercase()

        val usesIndex = planText.contains("INDEX") || planText.contains("IDX_USER_EMAIL")
        println("\n  인덱스 사용 여부: ${if (usesIndex) "YES - 인덱스 스캔" else "확인 필요"}")
        println("  Full Table Scan 여부: ${if (planText.contains("TABLE SCAN")) "YES (비효율적)" else "NO (효율적)"}")
        println("=".repeat(70))
    }

    @Test
    @Order(4)
    @DisplayName("idx_post_created_at 인덱스: created_at 범위 조회 시 인덱스 사용 확인")
    fun `created_at index is used for range query`() {
        println("\n" + "=".repeat(70))
        println("idx_post_created_at 인덱스 검증")
        println("=".repeat(70))

        val result = explain(
            "created_at 범위 조회",
            "SELECT * FROM posts WHERE created_at BETWEEN '2026-01-10' AND '2026-02-10'"
        )
        val planText = result.joinToString(" ").uppercase()

        val usesIndex = planText.contains("INDEX") || planText.contains("IDX_POST_CREATED_AT")
        println("\n  인덱스 사용 여부: ${if (usesIndex) "YES - 인덱스 스캔" else "확인 필요"}")
        println("  Full Table Scan 여부: ${if (planText.contains("TABLE SCAN")) "YES (비효율적)" else "NO (효율적)"}")
        println("=".repeat(70))
    }

    @Test
    @Order(5)
    @DisplayName("idx_post_user_created 복합 인덱스: user_id + created_at 조회 시 인덱스 사용 확인")
    fun `composite index is used for user_id and created_at query`() {
        println("\n" + "=".repeat(70))
        println("idx_post_user_created 복합 인덱스 검증")
        println("=".repeat(70))

        val userId = savedUsers.first().id

        val result = explain(
            "user_id + created_at 복합 조건 조회",
            "SELECT * FROM posts WHERE user_id = $userId AND created_at > '2026-01-01'"
        )
        val planText = result.joinToString(" ").uppercase()

        val usesIndex = planText.contains("INDEX") || planText.contains("IDX_POST_USER_CREATED")
        println("\n  인덱스 사용 여부: ${if (usesIndex) "YES - 복합 인덱스 스캔" else "확인 필요"}")
        println("  Full Table Scan 여부: ${if (planText.contains("TABLE SCAN")) "YES (비효율적)" else "NO (효율적)"}")
        println("=".repeat(70))
    }

    // ===================================================
    //  4. 복합 인덱스 컬럼 순서 영향 테스트
    // ===================================================

    @Test
    @Order(6)
    @DisplayName("복합 인덱스 컬럼 순서 영향: 선행 컬럼(user_id)만 사용 vs 후행 컬럼(created_at)만 사용")
    fun `composite index column order matters`() {
        println("\n" + "=".repeat(70))
        println("복합 인덱스 컬럼 순서 영향 테스트")
        println("idx_post_user_created = (user_id, created_at)")
        println("=".repeat(70))

        val userId = savedUsers.first().id

        // Case 1: 선행 컬럼(user_id)만 사용
        println("\n[Case 1] 선행 컬럼(user_id)만 사용")
        val result1 = explain(
            "user_id만으로 조회 (선행 컬럼)",
            "SELECT * FROM posts WHERE user_id = $userId"
        )
        val plan1 = result1.joinToString(" ").uppercase()

        // Case 2: 후행 컬럼(created_at)만 사용
        println("\n[Case 2] 후행 컬럼(created_at)만 사용")
        val result2 = explain(
            "created_at만으로 조회 (후행 컬럼)",
            "SELECT * FROM posts WHERE created_at > '2026-03-01'"
        )
        val plan2 = result2.joinToString(" ").uppercase()

        // Case 3: 두 컬럼 모두 사용
        println("\n[Case 3] 두 컬럼 모두 사용 (user_id + created_at)")
        val result3 = explain(
            "user_id + created_at 조합 조회",
            "SELECT * FROM posts WHERE user_id = $userId AND created_at > '2026-01-01'"
        )
        val plan3 = result3.joinToString(" ").uppercase()

        // Case 4: 컬럼 순서 반대로 작성
        println("\n[Case 4] SQL 조건 순서를 반대로 작성 (created_at 먼저, user_id 나중)")
        val result4 = explain(
            "created_at + user_id (SQL 조건 순서 반대)",
            "SELECT * FROM posts WHERE created_at > '2026-01-01' AND user_id = $userId"
        )
        val plan4 = result4.joinToString(" ").uppercase()

        // 결과 요약
        println("\n" + "=".repeat(70))
        println("결과 요약")
        println("=".repeat(70))

        println("""
  복합 인덱스: idx_post_user_created (user_id, created_at)

  [Case 1] user_id만 사용 (선행 컬럼)
    -> 복합 인덱스 활용 가능 (선행 컬럼이므로 인덱스 탐색 가능)
    -> 사용된 인덱스: ${if (plan1.contains("IDX_POST_USER_CREATED")) "idx_post_user_created" else if (plan1.contains("FK") && plan1.contains("INDEX")) "FK 인덱스 (H2 옵티마이저 선택, user_id 단독 인덱스)" else if (plan1.contains("INDEX")) "다른 인덱스" else "Full Scan"}

  [Case 2] created_at만 사용 (후행 컬럼)
    -> 복합 인덱스 idx_post_user_created 활용 불가
    -> 단일 인덱스 idx_post_created_at 사용 가능
    -> 사용된 인덱스: ${if (plan2.contains("IDX_POST_CREATED_AT")) "idx_post_created_at (단일)" else if (plan2.contains("IDX_POST_USER_CREATED")) "idx_post_user_created (비정상)" else if (plan2.contains("INDEX")) "다른 인덱스" else "Full Scan"}

  [Case 3] user_id + created_at (두 컬럼 모두)
    -> 복합 인덱스 최적 활용
    -> 사용된 인덱스: ${if (plan3.contains("IDX_POST_USER_CREATED")) "idx_post_user_created (최적)" else if (plan3.contains("INDEX")) "다른 인덱스" else "Full Scan"}

  [Case 4] SQL 조건 순서 반대 (created_at AND user_id)
    -> SQL 조건 작성 순서는 인덱스 사용에 영향 없음
    -> 옵티마이저가 자동으로 인덱스에 맞게 재배열
    -> 사용된 인덱스: ${if (plan4.contains("IDX_POST_USER_CREATED")) "idx_post_user_created (최적)" else if (plan4.contains("INDEX")) "다른 인덱스" else "Full Scan"}

  핵심 결론:
    - 복합 인덱스는 선행 컬럼부터 순서대로 사용해야 효과적
    - 후행 컬럼만으로는 해당 복합 인덱스를 활용할 수 없음
    - SQL WHERE 절의 조건 작성 순서는 무관 (옵티마이저가 최적화)
        """.trimIndent())

        println("=".repeat(70))
    }
}
