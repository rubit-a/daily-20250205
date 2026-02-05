# Database 인덱싱 및 쿼리 최적화 체크리스트

## 1. 프로젝트 설정

### 의존성
- [x] `spring-boot-starter-data-jpa` 추가
- [x] `h2` 데이터베이스 추가

### application.yml 설정
- [x] H2 인메모리 DB 설정
- [x] H2 Console 활성화 (`/h2-console`)
- [x] JPA ddl-auto 설정 (`create-drop`)
- [x] SQL 로깅 설정 (`show_sql`, `format_sql`)
- [x] `hibernate.default_batch_fetch_size` 설정

---

## 2. JPA Entity 작성

### UserJpaEntity
- [x] `@Entity`, `@Table` 어노테이션
- [x] `@Id`, `@GeneratedValue` 설정
- [x] `@Column` 속성 정의 (nullable, length, unique)
- [x] email unique 인덱스 (`@Table(indexes = [...])`)
- [x] `@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)`

### PostJpaEntity
- [x] `@Entity`, `@Table` 어노테이션
- [x] `@ManyToOne(fetch = FetchType.LAZY)` - User 참조
- [x] `@JoinColumn(name = "user_id")` 외래 키
- [x] 인덱스: `idx_post_created_at` (created_at)
- [x] 복합 인덱스: `idx_post_user_created` (user_id, created_at)
- [x] `@OneToMany` - Comment 목록

### CommentJpaEntity
- [x] `@Entity`, `@Table` 어노테이션
- [x] `@ManyToOne(fetch = FetchType.LAZY)` - Post, User 참조

---

## 3. Repository 구현

### JPA Repository
- [x] `UserRepository` (JpaRepository 상속)
- [x] `PostRepository` (JpaRepository 상속)
- [x] `CommentRepository` (JpaRepository 상속)

### 커스텀 쿼리 메서드
- [x] `findByUserId()` - 사용자별 게시글 조회
- [x] `findByCreatedAtBetween()` - 기간별 조회

---

## 4. N+1 문제 해결

### 문제 확인
- [x] Post 목록 조회 시 User 정보 N+1 발생 확인 (6개 쿼리: 1 + 5 Users)
- [ ] Post 상세 조회 시 Comments N+1 발생 확인
- [x] SQL 로그에서 쿼리 횟수 기록 (해결 전)

### 해결 방법 1: Fetch Join (JPQL)
- [x] `@Query("SELECT p FROM PostJpaEntity p JOIN FETCH p.user")`
- [x] 해결 후 쿼리 횟수 확인 (1번으로 감소) ✓

### 해결 방법 2: @EntityGraph
- [x] `@EntityGraph(attributePaths = ["user", "comments"])`
- [x] Fetch Join과 비교 (둘 다 1개 쿼리, JOIN 타입 차이)

### 해결 방법 3: @BatchSize
- [x] Entity 또는 컬렉션에 `@BatchSize(size = 100)` 적용
- [x] IN 절로 묶어서 조회되는지 확인 (1+N → 1+1 쿼리)

---

## 5. 인덱스 최적화

### 인덱스 설정 확인
- [x] `idx_user_email` - email unique 인덱스
- [x] `idx_post_created_at` - 생성일 인덱스
- [x] `idx_post_user_created` - (user_id, created_at) 복합 인덱스

### 인덱스 효과 검증
- [ ] 테스트 데이터 삽입 (사용자 100명, 게시글 1000개, 댓글 5000개)
- [ ] H2 Console에서 `EXPLAIN` 실행
- [ ] 인덱스 사용 여부 확인
- [ ] 복합 인덱스 컬럼 순서 영향 테스트

---

## 6. 페이지네이션

### 기본 페이지네이션
- [ ] `Pageable` 파라미터 적용
- [ ] `Page<T>` 반환 (총 개수 포함)
- [ ] `Slice<T>` 반환 (총 개수 없이 - 더 효율적)

### 정렬
- [ ] `Sort.by("createdAt").descending()` 적용
- [ ] 복합 정렬 테스트

### 커버링 인덱스
- [ ] 페이지네이션 + 정렬이 인덱스를 타는지 확인

---

## 7. Controller & 테스트

### Controller
- [ ] `GET /api/posts` - 전체 조회 (페이지네이션)
- [ ] `GET /api/posts/{id}` - 상세 조회 (댓글 포함)
- [ ] `GET /api/users/{userId}/posts` - 사용자별 조회

### 테스트
- [x] `@DataJpaTest`로 Repository 테스트
- [x] N+1 해결 전/후 쿼리 횟수 비교 테스트 (6개 → 1개)
- [ ] 페이지네이션 동작 확인

---

## 8. 성능 측정 (선택)

- [ ] 쿼리 실행 시간 로깅
- [ ] 대량 데이터에서 인덱스 유무 비교
- [ ] Fetch Join vs @BatchSize 성능 비교

---

## 진행률

| 항목 | 완료 | 전체 |
|------|------|------|
| 프로젝트 설정 | 6 | 6 |
| JPA Entity | 12 | 12 |
| Repository | 5 | 5 |
| N+1 해결 | 8 | 8 |
| 인덱스 최적화 | 3 | 7 |
| 페이지네이션 | 0 | 6 |
| Controller & 테스트 | 2 | 6 |
| 성능 측정 | 0 | 3 |

**총 진행률**: 36 / 53 (68%)
