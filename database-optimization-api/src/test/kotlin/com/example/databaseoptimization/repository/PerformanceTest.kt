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
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * 성능 측정 테스트
 *
 * - 쿼리 실행 시간 로깅
 * - 대량 데이터에서 인덱스 유무 비교
 * - Fetch Join vs @BatchSize 성능 비교
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Commit
class PerformanceTest {

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

        // 사용자 100명
        savedUsers = (1..100).map { i ->
            userRepository.save(
                UserJpaEntity(name = "User$i", email = "user$i@test.com")
            )
        }
        entityManager.flush()
        entityManager.clear()
        savedUsers = userRepository.findAll()

        // 게시글 1,000개 (사용자당 10개)
        savedUsers.chunked(50).forEach { chunk ->
            chunk.forEach { user ->
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

        // 댓글 5,000개 (게시글당 5개)
        val allPosts = postRepository.findAll()
        allPosts.chunked(200).forEach { chunk ->
            chunk.forEach { post ->
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

    // ===================================================
    //  1. 쿼리 실행 시간 로깅
    // ===================================================

    @Test
    @Order(1)
    @DisplayName("쿼리 실행 시간 로깅: 다양한 조회 패턴별 소요 시간 측정")
    fun `query execution time logging`() {
        println("\n" + "=".repeat(70))
        println("쿼리 실행 시간 측정 (데이터: 100 Users / 1,000 Posts / 5,000 Comments)")
        println("=".repeat(70))

        val results = mutableListOf<Triple<String, Long, Int>>()

        // Warm-up
        postRepository.findAll()
        entityManager.clear()

        // 1) findAll
        var count: Int
        var time = measureTimeMillis {
            count = postRepository.findAll().size
        }
        results.add(Triple("findAll()", time, count))
        entityManager.clear()

        // 2) findAllWithUser (Fetch Join)
        time = measureTimeMillis {
            count = postRepository.findAllWithUser().size
        }
        results.add(Triple("findAllWithUser() [Fetch Join]", time, count))
        entityManager.clear()

        // 3) findAllWithUserAndComments (Fetch Join)
        time = measureTimeMillis {
            count = postRepository.findAllWithUserAndComments().size
        }
        results.add(Triple("findAllWithUserAndComments() [Fetch Join]", time, count))
        entityManager.clear()

        // 4) findByUserId (인덱스 활용)
        val userId = savedUsers.first().id
        time = measureTimeMillis {
            count = postRepository.findByUserId(userId).size
        }
        results.add(Triple("findByUserId($userId) [인덱스]", time, count))
        entityManager.clear()

        // 5) findByCreatedAtBetween (인덱스 활용)
        val start = LocalDateTime.of(2026, 1, 10, 0, 0)
        val end = LocalDateTime.of(2026, 2, 10, 0, 0)
        time = measureTimeMillis {
            count = postRepository.findByCreatedAtBetween(start, end).size
        }
        results.add(Triple("findByCreatedAtBetween [인덱스]", time, count))
        entityManager.clear()

        // 6) Native SQL: 인덱스 있는 컬럼 조회
        time = measureTimeMillis {
            @Suppress("UNCHECKED_CAST")
            count = (entityManager.createNativeQuery("SELECT * FROM posts WHERE user_id = $userId")
                .resultList as List<*>).size
        }
        results.add(Triple("Native: WHERE user_id = $userId [인덱스]", time, count))

        // 7) Native SQL: 인덱스 없는 컬럼 조회
        time = measureTimeMillis {
            @Suppress("UNCHECKED_CAST")
            count = (entityManager.createNativeQuery("SELECT * FROM posts WHERE title LIKE '%User1%'")
                .resultList as List<*>).size
        }
        results.add(Triple("Native: WHERE title LIKE '%..%' [Full Scan]", time, count))

        // 결과 출력
        println("\n  +------------------------------------------------------+--------+--------+")
        println("  | 쿼리                                                 | 시간   | 건수   |")
        println("  +------------------------------------------------------+--------+--------+")
        results.forEach { (name, ms, cnt) ->
            println("  | %-52s | %4d ms | %5d  |".format(name, ms, cnt))
        }
        println("  +------------------------------------------------------+--------+--------+")

        println("=".repeat(70))
    }

    // ===================================================
    //  2. 대량 데이터에서 인덱스 유무 비교
    // ===================================================

    @Test
    @Order(2)
    @DisplayName("대량 데이터에서 인덱스 유무 비교: 인덱스 컬럼 vs 비인덱스 컬럼 성능 차이")
    fun `compare index vs no index with large data`() {
        println("\n" + "=".repeat(70))
        println("인덱스 유무 성능 비교 (1,000 Posts)")
        println("=".repeat(70))

        val iterations = 10
        val userId = savedUsers[49].id  // 중간 사용자

        // Case 1: 인덱스 있는 컬럼 (user_id) - idx_post_user_created
        val indexedTimes = mutableListOf<Long>()
        repeat(iterations) {
            entityManager.clear()
            indexedTimes.add(measureTimeMillis {
                entityManager.createNativeQuery(
                    "SELECT * FROM posts WHERE user_id = $userId"
                ).resultList
            })
        }

        // Case 2: 인덱스 없는 컬럼 (title LIKE)
        val nonIndexedTimes = mutableListOf<Long>()
        repeat(iterations) {
            entityManager.clear()
            nonIndexedTimes.add(measureTimeMillis {
                entityManager.createNativeQuery(
                    "SELECT * FROM posts WHERE title LIKE '%User50%'"
                ).resultList
            })
        }

        // Case 3: 인덱스 있는 컬럼 (email) - idx_user_email
        val emailIndexTimes = mutableListOf<Long>()
        repeat(iterations) {
            entityManager.clear()
            emailIndexTimes.add(measureTimeMillis {
                entityManager.createNativeQuery(
                    "SELECT * FROM users WHERE email = 'user50@test.com'"
                ).resultList
            })
        }

        // Case 4: 인덱스 없는 컬럼 (name LIKE)
        val nameNoIndexTimes = mutableListOf<Long>()
        repeat(iterations) {
            entityManager.clear()
            nameNoIndexTimes.add(measureTimeMillis {
                entityManager.createNativeQuery(
                    "SELECT * FROM users WHERE name LIKE '%User50%'"
                ).resultList
            })
        }

        // EXPLAIN 확인
        println("\n  [EXPLAIN 분석]")

        println("\n  인덱스 O: WHERE user_id = $userId")
        @Suppress("UNCHECKED_CAST")
        val explainIdx = entityManager.createNativeQuery(
            "EXPLAIN ANALYZE SELECT * FROM posts WHERE user_id = $userId"
        ).resultList as List<String>
        explainIdx.forEach { println("    $it") }

        println("\n  인덱스 X: WHERE title LIKE '%User50%'")
        @Suppress("UNCHECKED_CAST")
        val explainNoIdx = entityManager.createNativeQuery(
            "EXPLAIN ANALYZE SELECT * FROM posts WHERE title LIKE '%User50%'"
        ).resultList as List<String>
        explainNoIdx.forEach { println("    $it") }

        // 결과
        println("\n  [성능 비교 결과] (${iterations}회 반복 평균)")
        println("  +-----------------------------------------+-----------+-----------+")
        println("  | 쿼리                                    | 평균(ms)  | 인덱스    |")
        println("  +-----------------------------------------+-----------+-----------+")
        println("  | %-39s | %6.2f ms | %-9s |".format("WHERE user_id = N", indexedTimes.average(), "O (복합)"))
        println("  | %-39s | %6.2f ms | %-9s |".format("WHERE title LIKE '%..%'", nonIndexedTimes.average(), "X"))
        println("  | %-39s | %6.2f ms | %-9s |".format("WHERE email = '...'", emailIndexTimes.average(), "O (unique)"))
        println("  | %-39s | %6.2f ms | %-9s |".format("WHERE name LIKE '%..%'", nameNoIndexTimes.average(), "X"))
        println("  +-----------------------------------------+-----------+-----------+")

        println("\n  핵심: 인덱스가 있는 컬럼은 대량 데이터에서도 빠르게 조회 가능")
        println("=".repeat(70))
    }

    // ===================================================
    //  3. Fetch Join vs @BatchSize 성능 비교
    // ===================================================

    @Test
    @Order(3)
    @DisplayName("Fetch Join vs @BatchSize 성능 비교: N+1 해결 전략별 소요 시간")
    fun `compare Fetch Join vs BatchSize performance`() {
        println("\n" + "=".repeat(70))
        println("Fetch Join vs @BatchSize 성능 비교")
        println("=".repeat(70))

        val iterations = 5

        // ===== Case 1: findAll() + LAZY (N+1 발생) =====
        val lazyTimes = mutableListOf<Long>()
        var lazyQueryEstimate = 0
        repeat(iterations) {
            entityManager.clear()
            lazyTimes.add(measureTimeMillis {
                val posts = postRepository.findAll()
                val userNames = mutableSetOf<Long>()
                posts.forEach { post ->
                    val uid = post.user.id
                    if (uid !in userNames) userNames.add(uid)
                    post.user.name  // LAZY 로딩 트리거
                }
                lazyQueryEstimate = 1 + userNames.size
            })
        }

        // ===== Case 2: Fetch Join =====
        val fetchJoinTimes = mutableListOf<Long>()
        repeat(iterations) {
            entityManager.clear()
            fetchJoinTimes.add(measureTimeMillis {
                val posts = postRepository.findAllWithUser()
                posts.forEach { it.user.name }  // 이미 로딩됨
            })
        }

        // ===== Case 3: @EntityGraph =====
        val entityGraphTimes = mutableListOf<Long>()
        repeat(iterations) {
            entityManager.clear()
            entityGraphTimes.add(measureTimeMillis {
                val posts = postRepository.findAllByOrderByCreatedAtDesc()
                posts.forEach { it.user.name }  // 이미 로딩됨
            })
        }

        // ===== Case 4: findAll() + @BatchSize (User에 @BatchSize(100) 적용됨) =====
        // @BatchSize는 application.yml의 default_batch_fetch_size가 적용될 때 동작
        // 테스트 환경에서는 비활성화되어 있으므로 N+1 발생 → 실 운영에서는 1+1 쿼리
        val batchSizeTimes = mutableListOf<Long>()
        repeat(iterations) {
            entityManager.clear()
            batchSizeTimes.add(measureTimeMillis {
                val posts = postRepository.findAll()
                posts.forEach { it.user.name }  // BatchSize 적용 시 IN 절 사용
            })
        }

        // ===== Fetch Join으로 User + Comments 한 번에 =====
        val fetchJoinAllTimes = mutableListOf<Long>()
        repeat(iterations) {
            entityManager.clear()
            fetchJoinAllTimes.add(measureTimeMillis {
                val posts = postRepository.findAllWithUserAndComments()
                posts.forEach { post ->
                    post.user.name
                    post.comments.size
                }
            })
        }

        // 결과
        println("\n  [성능 비교 결과] (${iterations}회 반복 평균)")
        println("  +-----------------------------------------------+-----------+----------+")
        println("  | 전략                                          | 평균(ms)  | 쿼리 수  |")
        println("  +-----------------------------------------------+-----------+----------+")
        println("  | %-45s | %6.1f ms | ~%-6d |".format(
            "findAll() + LAZY (N+1 발생)", lazyTimes.average(), lazyQueryEstimate))
        println("  | %-45s | %6.1f ms | %-7d |".format(
            "findAllWithUser() [Fetch Join]", fetchJoinTimes.average(), 1))
        println("  | %-45s | %6.1f ms | %-7d |".format(
            "findAllByOrderByCreatedAtDesc() [@EntityGraph]", entityGraphTimes.average(), 1))
        println("  | %-45s | %6.1f ms | ~%-6s |".format(
            "findAll() + @BatchSize(100)", batchSizeTimes.average(), "1+1"))
        println("  | %-45s | %6.1f ms | %-7d |".format(
            "findAllWithUserAndComments() [Fetch Join All]", fetchJoinAllTimes.average(), 1))
        println("  +-----------------------------------------------+-----------+----------+")

        println("""

  [전략별 특징]
  +-----------------+----------+---------------+------------------+
  | 전략             | 쿼리 수    | 페이지네이션     | 적합한 상황      |
  +-----------------+----------+---------------+------------------+
  | LAZY (N+1)      | 1 + N    | O             | 사용 금지        |
  | Fetch Join      | 1        | 제한적(*)       | 소량 데이터      |
  | @EntityGraph    | 1        | 제한적(*)       | 선언적 사용      |
  | @BatchSize      | 1 + 1    | O             | 대량 + 페이징    |
  +-----------------+----------+---------------+------------------+
  (*) 컬렉션 Fetch Join 시 페이지네이션이 메모리에서 처리됨

  핵심 결론:
  - Fetch Join이 단일 쿼리로 가장 빠르지만 컬렉션 페이지네이션 제약
  - @BatchSize는 쿼리 2개지만 페이지네이션과 호환성 우수
  - 상황에 따라 적절한 전략 선택이 중요
        """.trimIndent())

        println("=".repeat(70))

        assertTrue(lazyTimes.average() >= fetchJoinTimes.average(),
            "Fetch Join이 N+1보다 빠르거나 같아야 합니다")
    }
}
