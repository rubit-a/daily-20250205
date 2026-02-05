# JPA와 Database 인덱싱 아키텍처

## Spring Data JPA 개요

### JPA란?
Java Persistence API의 약자로, 객체와 관계형 데이터베이스 테이블을 매핑하는 ORM 표준 명세이다.
Spring Data JPA는 JPA를 더 편리하게 사용할 수 있도록 추상화한 모듈이다.

### 핵심 개념
```
Kotlin 객체 (Entity) <--JPA 매핑--> Database 테이블 (Row)
```

- `@Entity`: 이 클래스가 DB 테이블과 매핑된다는 선언
- `@Table`: 매핑할 테이블명 지정
- `@Id`: 기본 키(PK) 필드 지정
- `@Column`: 컬럼 속성 지정 (nullable, length 등)
- `@GeneratedValue`: PK 자동 생성 전략

---

## Entity 관계 매핑

### 1:N 관계 (OneToMany)
```
User (1) ----< Post (N)
                  |
Post (1) ----< Comment (N)
```

- `@ManyToOne`: N쪽(자식)에서 1쪽(부모)을 참조
- `@OneToMany`: 1쪽(부모)에서 N쪽(자식) 목록을 참조
- `mappedBy`: 연관 관계의 주인이 아닌 쪽에 지정

### 연관 관계의 주인
- FK(외래 키)를 가진 쪽이 연관 관계의 주인
- 주인만이 DB에 값을 저장/수정할 수 있음
- 주인이 아닌 쪽은 읽기만 가능

```kotlin
// Post가 연관 관계의 주인 (FK를 가짐)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
val user: UserJpaEntity

// User는 주인이 아님 (mappedBy로 읽기만)
@OneToMany(mappedBy = "user")
val posts: List<PostJpaEntity> = emptyList()
```

---

## FetchType: LAZY vs EAGER

### EAGER (즉시 로딩)
```
User 조회 → User + Posts 한 번에 로딩
```
- 연관 엔티티를 항상 함께 조회
- 불필요한 데이터까지 로딩하여 성능 저하 가능

### LAZY (지연 로딩)
```
User 조회 → User만 로딩
User.posts 접근 → 그때 Posts 조회 쿼리 실행
```
- 실제로 접근할 때 쿼리 실행
- 기본적으로 LAZY를 사용하고, 필요할 때 Fetch Join으로 최적화

**원칙: @ManyToOne, @OneToMany 모두 LAZY로 설정하는 것을 기본으로 한다.**

---

## N+1 문제

### 문제 상황
```
-- 모든 사용자 조회 (1번 쿼리)
SELECT * FROM users;          -- 결과: 10명

-- 각 사용자의 게시글 조회 (N번 쿼리)
SELECT * FROM posts WHERE user_id = 1;
SELECT * FROM posts WHERE user_id = 2;
SELECT * FROM posts WHERE user_id = 3;
... (10번 반복)
```
총 11번의 쿼리가 실행됨 → **1 + N 문제**

### 해결 방법 1: Fetch Join (JPQL)
```kotlin
@Query("SELECT u FROM UserJpaEntity u JOIN FETCH u.posts")
fun findAllWithPosts(): List<UserJpaEntity>
```
```sql
-- 1번의 쿼리로 해결
SELECT u.*, p.* FROM users u
JOIN posts p ON u.id = p.user_id;
```

### 해결 방법 2: @EntityGraph
```kotlin
@EntityGraph(attributePaths = ["posts"])
fun findAll(): List<UserJpaEntity>
```
- 어노테이션 기반으로 Fetch Join과 동일한 효과
- JPQL 작성 없이 사용 가능

### 해결 방법 3: Batch Size
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```
```sql
-- IN 절로 묶어서 조회
SELECT * FROM posts WHERE user_id IN (1, 2, 3, ... , 10);
```

---

## 인덱스 (Index)

### 인덱스란?
데이터베이스에서 검색 속도를 높이기 위한 자료 구조.
책의 목차와 유사하다.

```
인덱스 없이: 테이블 전체를 순차 탐색 (Full Table Scan)
인덱스 있으면: B-Tree를 통해 빠르게 찾음 (Index Scan)
```

### B-Tree 인덱스 구조
```
        [M]
       /   \
    [D,H]   [P,T]
   / | \    / | \
 [A-C][E-G][I-L] [N-O][Q-S][U-Z]
```
- 데이터가 정렬된 상태로 유지됨
- 검색 시 O(log N) 시간 복잡도
- INSERT/UPDATE 시 인덱스도 갱신해야 하므로 쓰기 성능 저하

### 인덱스가 효과적인 경우
- WHERE 절에서 자주 사용되는 컬럼
- JOIN 조건에 사용되는 컬럼 (FK)
- ORDER BY에 사용되는 컬럼
- 카디널리티(고유 값 수)가 높은 컬럼 (예: email)

### 인덱스가 비효과적인 경우
- 테이블 데이터가 적을 때 (Full Scan이 더 빠름)
- INSERT/UPDATE/DELETE가 매우 빈번한 컬럼
- 카디널리티가 낮은 컬럼 (예: gender - M/F)
- 거의 조회하지 않는 컬럼

### JPA에서 인덱스 설정
```kotlin
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_user_email", columnList = "email", unique = true),
        Index(name = "idx_user_created_at", columnList = "created_at")
    ]
)
class UserJpaEntity(...)
```

### 복합 인덱스
```kotlin
@Table(
    indexes = [
        Index(name = "idx_post_user_created", columnList = "user_id, created_at")
    ]
)
```
- 컬럼 순서가 중요함
- `(user_id, created_at)` 인덱스는 `user_id` 단독 검색에도 사용 가능
- 하지만 `created_at` 단독 검색에는 사용 불가 (선두 컬럼 원칙)

---

## Domain Entity vs JPA Entity

### 분리하는 이유
```
Domain Entity (순수 비즈니스)  ≠  JPA Entity (DB 매핑)
         |                              |
    비즈니스 규칙                  @Entity, @Column
    validate()                   @ManyToOne, @JoinColumn
    비즈니스 메서드               JPA 생명주기 콜백
```

### 변환 흐름
```
Controller → UseCase → Domain Entity
                           ↕ (Mapper)
              Repository → JPA Entity → Database
```

---

## 페이지네이션

### Spring Data JPA의 Pageable
```kotlin
fun findAll(pageable: Pageable): Page<PostJpaEntity>
```

### Page vs Slice
- `Page`: 전체 개수를 포함 (COUNT 쿼리 추가 실행)
- `Slice`: 다음 페이지 존재 여부만 확인 (COUNT 없음, 더 빠름)

### 정렬
```kotlin
val pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending())
```

---

## EXPLAIN (실행 계획)

### 쿼리 성능 분석
```sql
EXPLAIN ANALYZE SELECT * FROM users WHERE email = 'test@example.com';
```

### 주요 확인 항목
- **Seq Scan**: 전체 테이블 순차 탐색 (인덱스 미사용)
- **Index Scan**: 인덱스를 사용한 탐색
- **cost**: 예상 비용 (낮을수록 좋음)
- **rows**: 예상 결과 행 수
