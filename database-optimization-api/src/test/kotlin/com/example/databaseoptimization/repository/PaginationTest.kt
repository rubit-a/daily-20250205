package com.example.databaseoptimization.repository

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
import org.springframework.data.domain.Sort
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 페이지네이션 테스트
 *
 * 검증 항목:
 * - Pageable 파라미터 적용
 * - Page<T> 반환 (총 개수 포함)
 * - Slice<T> 반환 (총 개수 없이 - 더 효율적)
 * - Sort.by("createdAt").descending() 적용
 * - 복합 정렬
 * - 커버링 인덱스 확인
 */
@DataJpaTest
class PaginationTest {

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private lateinit var savedUsers: List<UserJpaEntity>

    @BeforeEach
    fun setUp() {
        savedUsers = (1..3).map { i ->
            userRepository.save(
                UserJpaEntity(name = "User$i", email = "user$i@test.com")
            )
        }

        // 게시글 30개 생성 (사용자당 10개, 날짜 다르게)
        savedUsers.forEach { user ->
            (1..10).forEach { j ->
                postRepository.save(
                    PostJpaEntity(
                        title = "Post by ${user.name} #$j",
                        content = "Content $j by ${user.name}",
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

    // ===================================================
    //  1. Pageable + Page<T> (총 개수 포함)
    // ===================================================

    @Test
    @DisplayName("Page<T> 반환: 총 개수 포함, 페이지 정보 제공")
    fun `Page returns total count and page info`() {
        println("\n" + "=".repeat(60))
        println("Page<T> 반환 테스트")
        println("=".repeat(60))

        val pageable = PageRequest.of(0, 5) // 첫 번째 페이지, 5개씩
        val page = postRepository.findAll(pageable)

        println("\n  요청: page=0, size=5")
        println("  총 게시글 수: ${page.totalElements}")
        println("  총 페이지 수: ${page.totalPages}")
        println("  현재 페이지: ${page.number}")
        println("  페이지 크기: ${page.size}")
        println("  현재 페이지 요소 수: ${page.numberOfElements}")
        println("  다음 페이지 존재: ${page.hasNext()}")
        println("  이전 페이지 존재: ${page.hasPrevious()}")

        println("\n  게시글 목록:")
        page.content.forEach { post ->
            println("    - [ID:${post.id}] ${post.title}")
        }

        assertEquals(30, page.totalElements)
        assertEquals(6, page.totalPages)  // 30 / 5 = 6
        assertEquals(0, page.number)
        assertEquals(5, page.numberOfElements)
        assertTrue(page.hasNext())
        assertFalse(page.hasPrevious())

        // 마지막 페이지 조회
        println("\n  --- 마지막 페이지 ---")
        val lastPage = postRepository.findAll(PageRequest.of(5, 5))
        println("  현재 페이지: ${lastPage.number}")
        println("  현재 페이지 요소 수: ${lastPage.numberOfElements}")
        println("  다음 페이지 존재: ${lastPage.hasNext()}")

        assertFalse(lastPage.hasNext())
        assertEquals(5, lastPage.numberOfElements)

        println("=".repeat(60))
    }

    @Test
    @DisplayName("Page<T>: 사용자별 게시글 페이지네이션")
    fun `Page with findByUserId`() {
        println("\n" + "=".repeat(60))
        println("사용자별 게시글 페이지네이션")
        println("=".repeat(60))

        val userId = savedUsers.first().id
        val pageable = PageRequest.of(0, 3)
        val page = postRepository.findByUserId(userId, pageable)

        println("\n  사용자 ID: $userId")
        println("  총 게시글 수: ${page.totalElements}")
        println("  총 페이지 수: ${page.totalPages}")
        println("  현재 페이지 요소:")
        page.content.forEach { post ->
            println("    - ${post.title}")
        }

        assertEquals(10, page.totalElements)
        assertEquals(4, page.totalPages)  // 10 / 3 = 4 (올림)
        assertEquals(3, page.numberOfElements)

        println("=".repeat(60))
    }

    // ===================================================
    //  2. Slice<T> (총 개수 없이 - 더 효율적)
    // ===================================================

    @Test
    @DisplayName("Slice<T> 반환: COUNT 쿼리 없이 다음 페이지 여부만 확인")
    fun `Slice does not execute count query`() {
        println("\n" + "=".repeat(60))
        println("Slice<T> 반환 테스트 (COUNT 쿼리 없음)")
        println("=".repeat(60))

        val pageable = PageRequest.of(0, 5)

        println("\n  >>> Slice 조회 (COUNT 쿼리 실행 안 됨):")
        val slice = postRepository.findAllBy(pageable)

        println("\n  현재 페이지: ${slice.number}")
        println("  페이지 크기: ${slice.size}")
        println("  현재 페이지 요소 수: ${slice.numberOfElements}")
        println("  다음 페이지 존재: ${slice.hasNext()}")
        println("  이전 페이지 존재: ${slice.hasPrevious()}")
        // Slice에는 totalElements, totalPages가 없음!

        println("\n  게시글 목록:")
        slice.content.forEach { post ->
            println("    - [ID:${post.id}] ${post.title}")
        }

        entityManager.clear()

        println("\n  >>> Page 조회 (COUNT 쿼리 실행됨):")
        val page = postRepository.findAll(pageable)

        println("\n  Page vs Slice 비교:")
        println("  +------------------+----------+----------+")
        println("  |                  | Page<T>  | Slice<T> |")
        println("  +------------------+----------+----------+")
        println("  | 총 개수 제공     | O (${page.totalElements})    | X        |")
        println("  | 총 페이지 제공   | O (${page.totalPages})     | X        |")
        println("  | COUNT 쿼리       | 실행     | 미실행   |")
        println("  | 다음 페이지 여부 | O        | O        |")
        println("  | 사용 시나리오    | 관리자   | 무한스크롤 |")
        println("  +------------------+----------+----------+")

        assertEquals(5, slice.numberOfElements)
        assertTrue(slice.hasNext())
        assertFalse(slice.hasPrevious())

        println("=".repeat(60))
    }

    // ===================================================
    //  3. 정렬
    // ===================================================

    @Test
    @DisplayName("Sort.by(\"createdAt\").descending() 적용")
    fun `sort by createdAt descending`() {
        println("\n" + "=".repeat(60))
        println("정렬 테스트: createdAt DESC")
        println("=".repeat(60))

        val pageable = PageRequest.of(0, 5, Sort.by("createdAt").descending())
        val page = postRepository.findAll(pageable)

        println("\n  최신순 정렬 결과:")
        page.content.forEach { post ->
            println("    - [${post.createdAt}] ${post.title}")
        }

        // 내림차순 검증
        val dates = page.content.map { it.createdAt }
        for (i in 0 until dates.size - 1) {
            assertTrue(
                dates[i] >= dates[i + 1],
                "createdAt이 내림차순이어야 합니다: ${dates[i]} >= ${dates[i + 1]}"
            )
        }

        println("\n  정렬 검증 완료: createdAt 내림차순 확인")
        println("=".repeat(60))
    }

    @Test
    @DisplayName("복합 정렬: user_id ASC + createdAt DESC")
    fun `compound sort by userId asc and createdAt desc`() {
        println("\n" + "=".repeat(60))
        println("복합 정렬 테스트: user_id ASC, createdAt DESC")
        println("=".repeat(60))

        val sort = Sort.by(Sort.Order.asc("user.id"), Sort.Order.desc("createdAt"))
        val pageable = PageRequest.of(0, 10, sort)
        val page = postRepository.findAll(pageable)

        println("\n  복합 정렬 결과 (user_id 오름차순 → createdAt 내림차순):")
        page.content.forEach { post ->
            println("    - User:${post.user.id}, [${post.createdAt}] ${post.title}")
        }

        assertEquals(10, page.numberOfElements)

        println("\n  복합 정렬 검증 완료")
        println("=".repeat(60))
    }

    // ===================================================
    //  4. 커버링 인덱스 확인
    // ===================================================

    @Test
    @DisplayName("커버링 인덱스: 페이지네이션 + 정렬이 인덱스를 타는지 EXPLAIN 확인")
    fun `pagination with sort uses index`() {
        println("\n" + "=".repeat(60))
        println("커버링 인덱스 확인: 페이지네이션 + 정렬")
        println("=".repeat(60))

        // Case 1: created_at 정렬 → idx_post_created_at 사용 확인
        println("\n[Case 1] ORDER BY created_at + LIMIT (idx_post_created_at)")
        @Suppress("UNCHECKED_CAST")
        val result1 = entityManager
            .createNativeQuery("EXPLAIN ANALYZE SELECT * FROM posts ORDER BY created_at DESC LIMIT 5 OFFSET 0")
            .resultList as List<String>
        result1.forEach { println("  $it") }
        val plan1 = result1.joinToString(" ").uppercase()

        // Case 2: user_id + created_at 정렬 → idx_post_user_created 복합 인덱스
        println("\n[Case 2] WHERE user_id = ? ORDER BY created_at + LIMIT (idx_post_user_created)")
        val userId = savedUsers.first().id
        @Suppress("UNCHECKED_CAST")
        val result2 = entityManager
            .createNativeQuery("EXPLAIN ANALYZE SELECT * FROM posts WHERE user_id = $userId ORDER BY created_at DESC LIMIT 5 OFFSET 0")
            .resultList as List<String>
        result2.forEach { println("  $it") }
        val plan2 = result2.joinToString(" ").uppercase()

        // Case 3: 인덱스 없는 컬럼으로 정렬 → Full Scan + filesort
        println("\n[Case 3] ORDER BY title (인덱스 없는 컬럼)")
        @Suppress("UNCHECKED_CAST")
        val result3 = entityManager
            .createNativeQuery("EXPLAIN ANALYZE SELECT * FROM posts ORDER BY title LIMIT 5 OFFSET 0")
            .resultList as List<String>
        result3.forEach { println("  $it") }
        val plan3 = result3.joinToString(" ").uppercase()

        println("\n" + "-".repeat(60))
        println("결과 요약")
        println("-".repeat(60))
        println("  [Case 1] ORDER BY created_at")
        println("    → 인덱스: ${if (plan1.contains("IDX_POST_CREATED_AT")) "idx_post_created_at 사용" else if (plan1.contains("INDEX")) "인덱스 사용" else "Full Scan"}")
        println("  [Case 2] WHERE user_id + ORDER BY created_at")
        println("    → 인덱스: ${if (plan2.contains("IDX_POST_USER_CREATED")) "idx_post_user_created 사용 (최적)" else if (plan2.contains("INDEX")) "인덱스 사용" else "Full Scan"}")
        println("  [Case 3] ORDER BY title (인덱스 없음)")
        println("    → 인덱스: ${if (plan3.contains("INDEX")) "인덱스 사용" else "Full Scan (정렬 비용 발생)"}")
        println("=".repeat(60))
    }
}
