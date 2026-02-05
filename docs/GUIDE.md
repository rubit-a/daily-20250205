# Day 2: 단계별 구현 가이드

## 개요
Day 1에서 구현한 Clean Architecture 프로젝트를 기반으로 JPA를 도입하고, 연관 관계 엔티티를 추가한 뒤 인덱싱과 쿼리 최적화를 적용한다.

---

## Phase 1: JPA 의존성 추가 및 설정

### 1-1. build.gradle.kts 의존성 추가
```kotlin
dependencies {
    // 기존 의존성 유지
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JPA 추가
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // H2 Database (개발/테스트용 인메모리 DB)
    runtimeOnly("com.h2database:h2")

    // SQL 로깅 (선택)
    implementation("com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.9.1")
}
```

### 1-2. application.yml 설정
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  h2:
    console:
      enabled: true
      path: /h2-console

  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        format_sql: true
        show_sql: true
        default_batch_fetch_size: 100

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 1-3. 설정 확인
- 서버 실행 후 `http://localhost:8080/h2-console` 접속
- JDBC URL: `jdbc:h2:mem:testdb` 로 접속 확인

---

## Phase 2: JPA Entity 작성

### 2-1. UserJpaEntity 생성
`data/entity/UserJpaEntity.kt` 파일 생성

- `@Entity`, `@Table` 어노테이션 추가
- `@Id`, `@GeneratedValue` 로 PK 설정
- `@Column` 으로 컬럼 속성 정의
- `@Table(indexes = [...])` 로 인덱스 설정
- email에 unique 인덱스 추가

### 2-2. PostJpaEntity 생성
`data/entity/PostJpaEntity.kt` 파일 생성

- User와 N:1 관계 (`@ManyToOne`)
- `@JoinColumn(name = "user_id")` 로 FK 설정
- `FetchType.LAZY` 설정
- title, content, createdAt 필드

### 2-3. CommentJpaEntity 생성
`data/entity/CommentJpaEntity.kt` 파일 생성

- Post와 N:1 관계 (`@ManyToOne`)
- User와 N:1 관계 (작성자)
- content, createdAt 필드

---

## Phase 3: Domain Entity 추가

### 3-1. Post Domain Entity
`domain/entity/Post.kt` 생성

- 순수 Kotlin data class
- id, title, content, userId, createdAt
- companion object에 create() 팩토리 메서드
- validate() 로 비즈니스 규칙 검증

### 3-2. Comment Domain Entity
`domain/entity/Comment.kt` 생성

- 순수 Kotlin data class
- id, content, postId, userId, createdAt

### 3-3. Domain Repository 인터페이스 추가
`domain/repository/PostRepository.kt`, `domain/repository/CommentRepository.kt` 생성

---

## Phase 4: Mapper 작성

### 4-1. UserMapper
`data/mapper/UserMapper.kt` 생성

```
Domain User  ←→  UserJpaEntity
  toJpaEntity()    toDomain()
```

### 4-2. PostMapper, CommentMapper
동일한 패턴으로 생성

---

## Phase 5: Repository 구현체 교체

### 5-1. JPA Repository 인터페이스 생성
`data/repository/UserJpaRepository.kt` 생성

```kotlin
interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    fun findByEmail(email: String): UserJpaEntity?
}
```

### 5-2. Repository 구현체 수정
기존 `UserRepositoryImpl` 을 InMemoryDataSource 대신 JpaRepository를 사용하도록 교체

```
기존: UserRepositoryImpl → InMemoryDataSource → ConcurrentHashMap
교체: UserRepositoryImpl → UserJpaRepository → H2 Database
```

이 교체가 가능한 이유: Day 1에서 인터페이스(UserRepository)로 분리했기 때문

---

## Phase 6: N+1 문제 확인 및 해결

### 6-1. N+1 발생 확인
- SQL 로그를 켜고 "사용자 목록 + 각 사용자의 게시글" 조회
- SELECT 쿼리 횟수 확인 (1 + N번 발생하는지)

### 6-2. Fetch Join 적용
```kotlin
@Query("SELECT u FROM UserJpaEntity u JOIN FETCH u.posts")
fun findAllWithPosts(): List<UserJpaEntity>
```

### 6-3. @EntityGraph 적용
```kotlin
@EntityGraph(attributePaths = ["posts"])
override fun findAll(): List<UserJpaEntity>
```

### 6-4. 해결 후 SQL 로그 비교
- 쿼리 횟수가 1번으로 줄었는지 확인

---

## Phase 7: 인덱스 적용 및 검증

### 7-1. 인덱스 설정
- email: unique 인덱스
- post.created_at: 정렬용 인덱스
- post(user_id, created_at): 복합 인덱스

### 7-2. 테스트 데이터 삽입
- 사용자 100명, 게시글 1000개, 댓글 5000개 정도의 데이터 삽입
- `@PostConstruct` 또는 `data.sql` 활용

### 7-3. 성능 비교
- 인덱스 적용 전/후 쿼리 속도 비교
- H2 Console에서 `EXPLAIN ANALYZE` 실행

---

## Phase 8: 페이지네이션

### 8-1. Pageable 적용
```kotlin
fun findByUserId(userId: Long, pageable: Pageable): Page<PostJpaEntity>
```

### 8-2. Controller에서 페이지 파라미터 수신
```kotlin
@GetMapping
fun getPosts(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "10") size: Int,
    @RequestParam(defaultValue = "createdAt") sort: String
): ResponseEntity<Page<PostResponse>>
```

---

## Phase 9: 테스트

### 9-1. Repository 테스트
- `@DataJpaTest` 로 JPA 관련 컴포넌트만 로딩
- 저장, 조회, 인덱스 활용 쿼리 테스트

### 9-2. UseCase 테스트
- MockK로 Repository를 mock하여 비즈니스 로직 테스트
- Day 1과 동일한 패턴

### 9-3. 통합 테스트
- `@SpringBootTest` 로 전체 컨텍스트 로딩
- 실제 H2 DB를 사용한 엔드투엔드 테스트
