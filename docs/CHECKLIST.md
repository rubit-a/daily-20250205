# Database 인덱싱 및 쿼리 최적화 체크리스트 (Kotlin/Spring)

## 프로젝트 설정

### 의존성 추가
- [x] `spring-boot-starter-data-jpa` 추가
- [x] `h2` 데이터베이스 추가
- [x] SQL 로깅 설정 (`show_sql`, `format_sql`)

### application.yml 설정
- [x] H2 인메모리 DB 설정
- [x] H2 Console 활성화
- [x] JPA ddl-auto 설정 (`create-drop`)
- [x] Hibernate SQL 로그 레벨 설정
- [x] batch_fetch_size 설정

---

## JPA Entity 작성

### UserJpaEntity
- [ ] `@Entity`, `@Table` 어노테이션
- [ ] `@Id`, `@GeneratedValue` 설정
- [ ] `@Column` 속성 정의 (nullable, length, unique)
- [ ] email unique 인덱스 (`@Table(indexes = [...])`)
- [ ] `@OneToMany(mappedBy = "user")` - Post 목록
- [ ] `FetchType.LAZY` 설정
- [ ] Domain Entity 변환 메서드 (`toDomain()`)

### PostJpaEntity
- [ ] `@Entity`, `@Table` 어노테이션
- [ ] title, content 필드
- [ ] `@ManyToOne(fetch = FetchType.LAZY)` - User 참조
- [ ] `@JoinColumn(name = "user_id")` 외래 키
- [ ] createdAt 필드
- [ ] 인덱스: created_at, (user_id + created_at) 복합 인덱스
- [ ] Domain Entity 변환 메서드

### CommentJpaEntity
- [ ] `@Entity`, `@Table` 어노테이션
- [ ] content 필드
- [ ] `@ManyToOne` - Post 참조
- [ ] `@ManyToOne` - User 참조 (작성자)
- [ ] createdAt 필드
- [ ] Domain Entity 변환 메서드

---

## Domain Entity 추가

### Post
- [ ] 순수 Kotlin data class
- [ ] id, title, content, userId, createdAt 필드
- [ ] companion object `create()` 팩토리 메서드
- [ ] validate() 비즈니스 규칙

### Comment
- [ ] 순수 Kotlin data class
- [ ] id, content, postId, userId, createdAt 필드
- [ ] companion object `create()` 팩토리 메서드

### Repository 인터페이스
- [ ] `PostRepository` 인터페이스 생성
- [ ] `CommentRepository` 인터페이스 생성

---

## Mapper 작성

- [ ] `UserMapper` (Domain User <-> UserJpaEntity)
- [ ] `PostMapper` (Domain Post <-> PostJpaEntity)
- [ ] `CommentMapper` (Domain Comment <-> CommentJpaEntity)

---

## Repository 구현체 교체

### JPA Repository 인터페이스
- [ ] `UserJpaRepository` (JpaRepository 상속)
- [ ] `PostJpaRepository` (JpaRepository 상속)
- [ ] `CommentJpaRepository` (JpaRepository 상속)

### 구현체 교체
- [ ] `UserRepositoryImpl` InMemory → JPA 교체
- [ ] `PostRepositoryImpl` 생성
- [ ] `CommentRepositoryImpl` 생성
- [ ] 기존 InMemoryDataSource 제거 (또는 유지)

---

## Use Case 추가

- [ ] `CreatePostUseCase` 게시글 생성
- [ ] `GetPostsByUserUseCase` 사용자별 게시글 조회
- [ ] `GetPostWithCommentsUseCase` 게시글 + 댓글 조회
- [ ] `CreateCommentUseCase` 댓글 생성

---

## Presentation Layer 추가

### DTO
- [ ] `PostRequest`, `PostResponse` DTO
- [ ] `CommentRequest`, `CommentResponse` DTO
- [ ] 페이지네이션 응답 DTO

### Controller
- [ ] `PostController` 생성
  - [ ] `POST /api/posts` 게시글 생성
  - [ ] `GET /api/posts` 전체 조회 (페이지네이션)
  - [ ] `GET /api/posts/{id}` 상세 조회 (댓글 포함)
  - [ ] `GET /api/users/{userId}/posts` 사용자별 조회
- [ ] `CommentController` 생성
  - [ ] `POST /api/posts/{postId}/comments` 댓글 생성

---

## N+1 문제 해결

### 문제 확인
- [ ] SQL 로그에서 N+1 발생 확인
- [ ] 쿼리 횟수 기록 (해결 전)

### 해결 적용
- [ ] Fetch Join (JPQL `JOIN FETCH`) 적용
- [ ] `@EntityGraph` 적용
- [ ] 해결 후 쿼리 횟수 확인 (1번으로 감소)

---

## 인덱스 적용

### 인덱스 설정
- [ ] email unique 인덱스
- [ ] post.created_at 인덱스
- [ ] post(user_id, created_at) 복합 인덱스

### 검증
- [ ] 테스트 데이터 삽입 (사용자 100명, 게시글 1000개)
- [ ] 인덱스 적용 전/후 쿼리 성능 비교
- [ ] EXPLAIN 실행 계획 확인

---

## 페이지네이션

- [ ] `Pageable` 파라미터 적용
- [ ] `Page<T>` 또는 `Slice<T>` 반환
- [ ] 정렬 기능 (Sort)
- [ ] Controller에서 page, size, sort 파라미터 수신

---

## 테스트

### Repository 테스트
- [ ] `@DataJpaTest` 활용
- [ ] 저장/조회 테스트
- [ ] 연관 관계 조회 테스트
- [ ] 페이지네이션 테스트

### UseCase 테스트
- [ ] MockK로 Repository mock
- [ ] 비즈니스 로직 검증

### 기능 테스트
- [ ] 서버 실행 확인
- [ ] POST/GET API 동작 확인
- [ ] N+1 해결 확인 (SQL 로그)

---

## 완료율

### 필수 항목
- 프로젝트 설정: 5 / 5 ✅
- JPA Entity: 0 / 18
- Domain Entity: 0 / 8
- Mapper: 0 / 3
- Repository 교체: 0 / 7
- Use Case: 0 / 4
- Presentation: 0 / 9
- N+1 해결: 0 / 4
- 인덱스: 0 / 6
- 페이지네이션: 0 / 4
- 테스트: 0 / 7

**총 진행률**: 5 / 75 (6%)
