# PTManager Backend

알바생·사장님을 연결하는 근무 스케줄·대타·근태 관리 앱 **PTManager**의 공통 백엔드.
**Spring Boot (Kotlin, JDK 21) REST API** 서버로, 직원용 앱과 사장용 앱이 이 서버를 통해 동일한 근무·매장·대타 데이터를 공유한다.

소규모 매장(카페·편의점·식당 등)의 단톡방·종이 근무표·수기 장부에 흩어져 있던 근무 운영을 하나로 디지털화하는 것이 목표다. 핵심은 **대타(대체 근무) 요청–지원–승인 워크플로우를 1급 기능으로** 다루고, **별도 하드웨어 없이 QR로 출근을 체크**하는 것이다.

## 프로젝트 구성 (3-tier)

| 리포지토리 | 설명 |
| --- | --- |
| **PTManagerBackend** (이 저장소) | 공통 REST API 서버 (Spring Boot · Kotlin) |
| PTManagerEmployee | 직원용 안드로이드 앱 — 스케줄 확인, QR 출근, 대타 요청·지원 |
| PTManagerEmployer | 사장용 안드로이드 앱 — 스케줄 편성, 대타 검토·승인, 출근 현황·인건비 |

두 앱은 동일한 5탭 골격(홈·스케줄·대타·공지·내 정보)을 공유하되 역할(EMPLOYEE/EMPLOYER)에 따라 화면을 다르게 구성하며, API 명세를 기준 문서로 삼아 서버·클라이언트가 병렬 개발한다.

## 기술 스택 (목표)

| 영역 | 기술 | 비고 |
| --- | --- | --- |
| 언어 | **Kotlin** (JDK 21) | |
| 프레임워크 | Spring Boot 3.5 (Web / Data JPA / Validation / Actuator) | |
| DB | PostgreSQL | 운영. 로컬은 임베디드 H2(`MODE=PostgreSQL`) |
| ORM | Spring Data JPA + Hibernate | 연관관계 대신 plain `Long` FK 컬럼 |
| 인증 | Spring Security + JWT (Bearer) | 역할 기반 RBAC |
| 실시간 | WebSocket (STOMP) | 대타 요청 즉시 알림 |
| 캐시 | Redis | 현재 근무자 목록·세션 |
| 푸시 | FCM | 대타·근태·공지 알림 |
| 파일 | AWS S3 | 공지 첨부 이미지 |
| API 문서 | Swagger (SpringDoc) | 안드로이드 팀과 스펙 공유 |
| 인프라 | AWS EC2 + RDS, GitHub Actions | 빌드/배포 |

도메인 컨트롤러는 Auth · Workplace · User · Shift · SwapRequest · Notice · Notification · Payroll · Health 로 분리되며, 공통 `ApiExceptionHandler`로 일관된 에러 응답을 제공한다.

## 실행

JDK 21이 필요하다. `./gradlew`는 `JAVA_HOME`이 가리키는 JDK로 실행되므로 먼저 설정해야 한다.
별도 JDK가 없으면 Android Studio 번들 JBR(`<AndroidStudio>/jbr`)을 그대로 써도 된다.

### JAVA_HOME 설정

> 경로 예시는 Android Studio 번들 JBR 기준이다. 설치 위치에 맞게 바꾼다.
> (예: `D:\Program Files\Android\Android Studio\jbr`)

**PowerShell — 현재 창에서만:**
```powershell
$env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
```

**Git Bash — 현재 창에서만:**
```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr"
```

**영구 설정 (새 터미널부터 적용):**
```powershell
setx JAVA_HOME "D:\Program Files\Android\Android Studio\jbr"
```

설정 확인:
```bash
java -version          # JAVA_HOME 적용 여부와 별개로 PATH의 java
"$JAVA_HOME/bin/java" -version   # Git Bash
```

### 실행

```bash
./gradlew bootRun        # PowerShell에서는 .\gradlew bootRun
```

| 환경 | URL |
| --- | --- |
| 로컬 | `http://localhost:8080` |
| 안드로이드 에뮬레이터 | `http://10.0.2.2:8080` |
| Swagger UI (로컬) | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON (로컬) | `http://localhost:8080/v3/api-docs` |
| **운영 Swagger** | `http://<ElasticIP>:8080/swagger-ui.html` (RDS PostgreSQL) — 로컬 브라우저에서 접속해 운영 API 테스트 |
| H2 콘솔 (로컬) | `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:ptmanager`, 사용자 `sa`) |

> Swagger의 서버 URL은 "접속한 호스트" 기준이라, 로컬에서 열면 로컬을, 운영 주소로 열면 운영 서버를 테스트한다.
> 보호된 API는 `POST /api/auth/login`으로 받은 `accessToken`을 우측 상단 **Authorize**에 입력하면 된다.

## 동작 확인

```bash
./gradlew test
curl http://localhost:8080/api/health

# 시드 계정으로 로그인
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"employee@ptmanager.test","password":"password"}'
```

## 아키텍처

- 계층 구조: 컨트롤러 → 서비스 → Spring Data 리포지토리.
- 영속화: **Spring Data JPA + 임베디드 H2**(`MODE=PostgreSQL`). 인메모리 데이터소스라 재시작마다 초기화되며 `config/DataSeeder`가 시드 데이터를 다시 적재한다. 도메인 타입은 JPA `@Entity` 클래스이고, 연관관계 대신 plain `Long` 외래키 컬럼을 써서 평면 JSON으로 직렬화된다.
- PostgreSQL로 교체하려면 데이터소스만 재정의(예: `SPRING_DATASOURCE_URL`)하고 `org.postgresql:postgresql` 드라이버를 추가하면 된다 — 엔티티는 DB 비종속이다. `src/main/resources/application.yml` 참고.
- 모든 에러는 `common/ApiExceptionHandler`를 통해 단일 `ApiError` 구조(`code`, `message`, `timestamp`, `fields`)로 반환된다: `400 BAD_REQUEST` / `400 VALIDATION_FAILED` / `404 NOT_FOUND`.

## API · 데이터 모델

- 전체 API 명세(목표): [API_SPEC.md](API_SPEC.md) — OpenAPI 3.0.3, 9개 태그 / 43개 엔드포인트.
- 데이터 모델(목표): [ERD.md](ERD.md) — PostgreSQL 11개 테이블.

대타 요청은 `PENDING`으로 생성되어 사장의 처리에 따라 `APPROVED` / `REJECTED`로 전이된다. 승인 시 선택된 지원자가 확정 대타자로 지정되고, 해당 근무의 근무자가 대타자로 재배정된다(요청자 이력은 보존).

## 시드 데이터

부팅 시 사용자 테이블이 비어 있으면 적재된다 (`config/DataSeeder`):

| 계정 | 역할 | 비밀번호 | 매장 (초대코드) |
| --- | --- | --- | --- |
| `employee@ptmanager.test` | EMPLOYEE | `password` | PT Manager Cafe (`CAFE01`) |
| `employer@ptmanager.test` | EMPLOYER | `password` | PT Manager Cafe (`CAFE01`) |

직원 계정에는 시드 근무 2건(오늘·내일)이 함께 들어간다.

## 구현 현황 (MVP)

> [API_SPEC.md](API_SPEC.md)와 [ERD.md](ERD.md)는 PTManager의 **목표 설계**다. 현재 백엔드는 MVP 초기 단계로, 그 설계의 일부만 구현되어 있다.

현재 동작하는 범위:

- **인증은 데모 스텁이다.** 비밀번호가 `password`로 하드코딩되어 있고, 발급되는 `dev-token-{id}`는 검증되지 않으며, 아직 역할 기반 권한(RBAC)도 없다. JWT + 리프레시 + 권한 처리가 다음 단계다.
- 도메인: 인증(로그인) · 매장/초대코드 · 근무/출근체크 · 대타 요청(요청·상태변경) · 가입 신청 · 공지 · 알림 · 인건비 집계.
- 아직 미구현: 회원가입·JWT·토큰 갱신, 대타 **지원(application)** 과 승인 분리, QR 토큰 검증, 페이지네이션, 알림 설정·FCM 디바이스 토큰, 공지 첨부/읽음(레드 닷), 알림 배지, WebSocket·Redis·S3 연동.

단계별 진행은 제안서의 향후 계획(요구사항 → 기본 기능 → 대타·QR·실시간 알림 → 인건비·공지·배포)을 따른다.
