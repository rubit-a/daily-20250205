# Day 2: 코드 예제 참조

## JPA Entity 예제

### UserJpaEntity
```kotlin
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_user_email", columnList = "email", unique = true)
    ]
)
class UserJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    val posts: MutableList<PostJpaEntity> = mutableListOf()
) {
    // JPA는 기본 생성자가 필요하므로 protected no-arg constructor 제공
    // kotlin-jpa 플러그인이 자동으로 처리

    fun toDomain(): User {
        return User(
            id = id.toString(),
            name = name,
            email = email,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(user: User): UserJpaEntity {
            return UserJpaEntity(
                name = user.name,
                email = user.email,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        }
    }
}
```

### PostJpaEntity
```kotlin
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
```

### CommentJpaEntity
```kotlin
@Entity
@Table(name = "comments")
class CommentJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 500)
    var content: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: PostJpaEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserJpaEntity,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

---

## JPA Repository 예제

### UserJpaRepository
```kotlin
interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    fun findByEmail(email: String): UserJpaEntity?

    // Fetch Join으로 N+1 해결
    @Query("SELECT u FROM UserJpaEntity u JOIN FETCH u.posts")
    fun findAllWithPosts(): List<UserJpaEntity>

    // EntityGraph로 N+1 해결
    @EntityGraph(attributePaths = ["posts"])
    fun findAllBy(): List<UserJpaEntity>
}
```

### PostJpaRepository
```kotlin
interface PostJpaRepository : JpaRepository<PostJpaEntity, Long> {
    fun findByUserId(userId: Long, pageable: Pageable): Page<PostJpaEntity>

    // 게시글 + 댓글 함께 조회
    @Query("SELECT p FROM PostJpaEntity p JOIN FETCH p.comments WHERE p.id = :id")
    fun findByIdWithComments(@Param("id") id: Long): PostJpaEntity?

    // 특정 기간 내 게시글 조회 (인덱스 활용)
    fun findByCreatedAtBetween(
        start: LocalDateTime,
        end: LocalDateTime,
        pageable: Pageable
    ): Page<PostJpaEntity>
}
```

---

## Mapper 예제

### UserMapper
```kotlin
object UserMapper {

    fun toJpaEntity(domain: User): UserJpaEntity {
        return UserJpaEntity(
            name = domain.name,
            email = domain.email,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(entity: UserJpaEntity): User {
        return User(
            id = entity.id.toString(),
            name = entity.name,
            email = entity.email,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
```

---

## Repository 구현체 교체 예제

### 기존 (InMemory)
```kotlin
@Repository
class UserRepositoryImpl(
    private val dataSource: InMemoryDataSource
) : UserRepository {
    override fun findById(id: String): User? = dataSource.findById(id)
    override fun save(user: User): User = dataSource.save(user)
}
```

### 교체 후 (JPA)
```kotlin
@Repository
class UserRepositoryImpl(
    private val jpaRepository: UserJpaRepository
) : UserRepository {

    override fun findById(id: String): User? {
        return jpaRepository.findById(id.toLong())
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun save(user: User): User {
        val entity = UserJpaEntity.fromDomain(user)
        val saved = jpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findByEmail(email: String): User? {
        return jpaRepository.findByEmail(email)?.toDomain()
    }

    override fun findAll(): List<User> {
        return jpaRepository.findAll().map { it.toDomain() }
    }
}
```

---

## N+1 문제 예제

### N+1 발생 코드
```kotlin
// UseCase에서
val users = userRepository.findAll()  // 1번 쿼리
users.forEach { user ->
    val posts = postRepository.findByUserId(user.id)  // N번 쿼리
}
```

### SQL 로그 (N+1 발생)
```sql
-- 1번: 사용자 전체 조회
SELECT * FROM users;

-- N번: 각 사용자의 게시글 조회
SELECT * FROM posts WHERE user_id = 1;
SELECT * FROM posts WHERE user_id = 2;
SELECT * FROM posts WHERE user_id = 3;
-- ... 사용자 수만큼 반복
```

### Fetch Join으로 해결
```kotlin
@Query("SELECT u FROM UserJpaEntity u JOIN FETCH u.posts")
fun findAllWithPosts(): List<UserJpaEntity>
```

### SQL 로그 (해결 후)
```sql
-- 1번의 쿼리로 해결
SELECT u.*, p.*
FROM users u
INNER JOIN posts p ON u.id = p.user_id;
```

---

## 페이지네이션 예제

### Controller
```kotlin
@GetMapping("/api/posts")
fun getPosts(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "10") size: Int,
    @RequestParam(defaultValue = "createdAt") sort: String
): ResponseEntity<Page<PostResponse>> {
    val pageable = PageRequest.of(page, size, Sort.by(sort).descending())
    val posts = getPostsUseCase.execute(pageable)
    return ResponseEntity.ok(posts)
}
```

### 응답 예시
```json
{
  "content": [
    { "id": 100, "title": "최신 글", "createdAt": "2025-02-05T10:00:00" },
    { "id": 99, "title": "이전 글", "createdAt": "2025-02-05T09:00:00" }
  ],
  "totalElements": 1000,
  "totalPages": 100,
  "number": 0,
  "size": 10,
  "first": true,
  "last": false
}
```

---

## 테스트 예제

### Repository 테스트 (@DataJpaTest)
```kotlin
@DataJpaTest
class UserJpaRepositoryTest {

    @Autowired
    lateinit var userJpaRepository: UserJpaRepository

    @Test
    fun `findByEmail should return user when exists`() {
        // Given
        val entity = UserJpaEntity(name = "홍길동", email = "hong@example.com")
        userJpaRepository.save(entity)

        // When
        val found = userJpaRepository.findByEmail("hong@example.com")

        // Then
        assertNotNull(found)
        assertEquals("홍길동", found?.name)
    }

    @Test
    fun `findAllWithPosts should fetch posts in single query`() {
        // Given: 사용자와 게시글 저장

        // When
        val users = userJpaRepository.findAllWithPosts()

        // Then: SQL 로그에서 쿼리가 1번만 실행되었는지 확인
        assertFalse(users.isEmpty())
    }
}
```

### 페이지네이션 테스트
```kotlin
@Test
fun `findByUserId should return paginated posts`() {
    // Given: 특정 사용자의 게시글 20개 저장
    val pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending())

    // When
    val page = postJpaRepository.findByUserId(userId, pageable)

    // Then
    assertEquals(10, page.content.size)  // 한 페이지에 10개
    assertEquals(20, page.totalElements) // 전체 20개
    assertEquals(2, page.totalPages)     // 총 2페이지
}
```

---

## build.gradle.kts 전체 예시

```kotlin
plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"  // JPA용 no-arg 플러그인
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("com.h2database:h2")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// JPA Entity에 기본 생성자를 자동 추가
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
```
