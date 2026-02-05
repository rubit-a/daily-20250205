# Day 2: Database 인덱싱 및 쿼리 최적화

## 과제 정보
- **날짜**: 2025년 2월 5일
- **난이도**: ⭐⭐⭐ (중급)
- **키워드**: `Spring Data JPA`, `인덱싱`, `쿼리 최적화`, `N+1 문제`, `JPQL`
- **선행 과제**: Day 1 - Clean Architecture 기반 REST API 설계

## 학습 목표

### 핵심 목표
1. **Spring Data JPA 기본 활용**
   - Entity 매핑 (`@Entity`, `@Table`, `@Column`)
   - JpaRepository 인터페이스 활용
   - Day 1의 InMemory → JPA 교체를 통한 DIP 체감

2. **인덱싱 전략 이해**
   - 인덱스의 원리와 B-Tree 구조
   - 단일 컬럼 인덱스 vs 복합 인덱스
   - 인덱스가 적합한 상황과 부적합한 상황

3. **쿼리 최적화 실습**
   - N+1 문제 이해 및 해결 (Fetch Join, EntityGraph)
   - JPQL과 QueryDSL 기본 사용
   - 실행 계획(EXPLAIN) 분석

## 기술 스택

### 필수
- **Language**: Kotlin 1.9+
- **Framework**: Spring Boot 3.x + Spring Data JPA
- **Database**: H2 (개발/테스트), PostgreSQL (선택)
- **Build Tool**: Gradle (Kotlin DSL)

### 선택
- **QueryDSL**: 타입 안전 동적 쿼리
- **p6spy**: SQL 로깅 및 분석
- **Flyway**: DB 마이그레이션

## 과제 요구사항

### 기능 요구사항
Day 1의 User 엔티티를 기반으로 확장:

1. **JPA Entity 전환**
   - InMemory DataSource → JPA 기반으로 교체
   - Domain Entity와 JPA Entity 분리 (또는 통합)
   - Repository 구현체를 JPA 기반으로 교체

2. **연관 관계 추가**
   - Post(게시글) 엔티티 추가 (User 1:N Post)
   - Comment(댓글) 엔티티 추가 (Post 1:N Comment)
   - 연관 관계 매핑 (`@OneToMany`, `@ManyToOne`)

3. **조회 API 구현**
   - 사용자별 게시글 목록 조회
   - 게시글 + 댓글 함께 조회
   - 페이지네이션 적용

4. **인덱스 적용**
   - 이메일 검색용 인덱스
   - 게시글 작성일 기준 정렬 인덱스
   - 복합 인덱스 (작성자 + 작성일)

5. **N+1 문제 해결**
   - N+1 발생 상황 확인 (SQL 로그)
   - Fetch Join으로 해결
   - `@EntityGraph`로 해결

### 아키텍처 요구사항

#### 1. 디렉토리 구조 (Day 1 확장)
```
src/main/kotlin/com/example/cleanarchitecture/
├── domain/
│   ├── entity/         # User, Post, Comment
│   ├── repository/     # UserRepository, PostRepository, CommentRepository
│   ├── usecase/        # 기존 + 신규 UseCase
│   └── exception/
├── data/
│   ├── entity/         # JPA Entity (UserJpaEntity, PostJpaEntity 등)
│   ├── repository/     # JPA 기반 구현체
│   └── mapper/         # Domain <-> JPA 변환
└── presentation/
    ├── controller/
    ├── dto/
    └── exception/
```

#### 2. 핵심 패턴
- **JPA Entity 분리**: Domain Entity ≠ JPA Entity
- **Mapper 패턴**: Entity 간 변환
- **Specification 패턴**: 동적 쿼리 조건 (선택)

## 참고 문서
- [ARCHITECTURE.md](./docs/ARCHITECTURE.md) - JPA와 인덱싱 이론
- [GUIDE.md](./docs/GUIDE.md) - 단계별 구현 가이드
- [CHECKLIST.md](./docs/CHECKLIST.md) - 구현 체크리스트
- [EXAMPLES.md](./docs/EXAMPLES.md) - 코드 예제

## 완료 기준

### 필수
- [ ] InMemory → JPA 전환 완료
- [ ] 연관 관계 엔티티 (Post, Comment) 추가
- [ ] N+1 문제 식별 및 해결
- [ ] 인덱스 적용 및 효과 확인
- [ ] 페이지네이션 구현

### 선택
- [ ] QueryDSL 적용
- [ ] EXPLAIN 분석 결과 문서화
- [ ] 벌크 데이터 삽입 후 성능 비교
