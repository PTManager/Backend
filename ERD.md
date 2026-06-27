# PTManager 데이터베이스 설계서 (ERD)

알바생·사장님을 연결하는 근무 스케줄·대타·근태 관리 앱 **PTManager**의 데이터 모델 최종본.

- 영속화: Spring Data JPA + PostgreSQL
- 관계 표현: JPA 연관관계 대신 plain `Long` 외래키 컬럼 (평면 JSON 직렬화 목적)
- 테이블명 `app_user`: `user`가 다수 DB에서 예약어이므로 (`@Table(name="app_user")`)
- 시각: 모든 타임스탬프는 `TIMESTAMPTZ`(UTC 저장, 표시 시 변환)

총 **11개 테이블**로 구성된다: `workplace`, `app_user`, `notification_setting`, `device_token`, `shift`, `swap_request`, `swap_application`, `join_request`, `notice`, `notice_attachment`, `notification`.

> 공지 읽음은 "읽은 사람 목록"(사장용) 기능을 MVP에서 제외하기로 하여, 별도 `notice_read` 테이블 대신 `app_user.last_read_notice_at` 컬럼 하나로 레드 닷(미확인 공지 표시)만 처리한다.

---

## 1. ER 다이어그램

```mermaid
erDiagram
    WORKPLACE ||--o{ APP_USER : "고용"
    WORKPLACE ||--o{ SHIFT : "보유"
    WORKPLACE ||--o{ SWAP_REQUEST : "보유"
    WORKPLACE ||--o{ JOIN_REQUEST : "가입신청"
    WORKPLACE ||--o{ NOTICE : "공지"
    APP_USER ||--o{ SHIFT : "근무(employee)"
    APP_USER ||--o{ SWAP_REQUEST : "요청(requester)"
    APP_USER |o--o{ SWAP_REQUEST : "확정대타(substitute)"
    APP_USER ||--o{ SWAP_APPLICATION : "지원(applicant)"
    APP_USER ||--o{ JOIN_REQUEST : "신청(user)"
    APP_USER ||--o{ NOTICE : "작성(author)"
    APP_USER ||--o{ NOTIFICATION : "수신(user)"
    APP_USER ||--o{ DEVICE_TOKEN : "보유"
    APP_USER ||--|| NOTIFICATION_SETTING : "설정"
    SHIFT ||--o{ SWAP_REQUEST : "대상근무"
    SWAP_REQUEST ||--o{ SWAP_APPLICATION : "지원받음"
    NOTICE ||--o{ NOTICE_ATTACHMENT : "첨부"

    WORKPLACE {
        bigint    id PK
        varchar   name
        varchar   address
        varchar   invite_code UK
        timestamp created_at
    }
    APP_USER {
        bigint    id PK
        varchar   email UK
        varchar   password
        varchar   name
        enum      role
        bigint    workplace_id FK
        int       hourly_wage
        timestamp last_read_notice_at
        timestamp created_at
        timestamp updated_at
    }
    NOTIFICATION_SETTING {
        bigint    user_id PK_FK
        boolean   swap_enabled
        boolean   notice_enabled
        boolean   attendance_enabled
        boolean   join_request_enabled
    }
    DEVICE_TOKEN {
        bigint    id PK
        bigint    user_id FK
        varchar   token UK
        enum      platform
        timestamp updated_at
    }
    SHIFT {
        bigint    id PK
        bigint    workplace_id FK
        bigint    employee_id  FK
        date      work_date
        time      start_time
        time      end_time
        timestamp checked_in_at
        enum      attendance_status
        timestamp created_at
        timestamp updated_at
    }
    SWAP_REQUEST {
        bigint    id PK
        bigint    workplace_id  FK
        bigint    shift_id      FK
        bigint    requester_id  FK
        bigint    substitute_id FK
        varchar   reason
        enum      status
        timestamp created_at
    }
    SWAP_APPLICATION {
        bigint    id PK
        bigint    swap_request_id FK
        bigint    applicant_id    FK
        enum      status
        timestamp created_at
    }
    JOIN_REQUEST {
        bigint    id PK
        bigint    workplace_id FK
        bigint    user_id      FK
        enum      status
        timestamp created_at
    }
    NOTICE {
        bigint    id PK
        bigint    workplace_id FK
        bigint    author_id    FK
        varchar   title
        clob      body
        timestamp created_at
    }
    NOTICE_ATTACHMENT {
        bigint    id PK
        bigint    notice_id FK
        varchar   file_url
        timestamp created_at
    }
    NOTIFICATION {
        bigint    id PK
        bigint    user_id FK
        enum      type
        varchar   message
        varchar   target_type
        bigint    target_id
        boolean   is_read
        timestamp created_at
    }
```

---

## 2. 테이블 및 컬럼 상세

### 2.1 `workplace` — 매장

하나의 아르바이트 매장. 모든 직원·근무·대타·공지의 소유 단위이며, 시스템의 최상위 테넌트 역할을 한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 매장 식별자 |
| `name` | VARCHAR(255) | NOT NULL | 매장 이름 (예: "시루 카페 정왕점") |
| `address` | VARCHAR(255) | NULL | 매장 주소 (선택) |
| `invite_code` | VARCHAR(16) | NOT NULL, UNIQUE | 직원 가입용 초대 코드. 사장이 매장 생성 시 발급, 직원은 이 코드로 가입 신청 |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 매장 생성 시각 |

### 2.2 `app_user` — 사용자 (직원/사장 공통)

직원(EMPLOYEE)과 사장(EMPLOYER)을 단일 테이블로 관리한다. `role`로 앱 경험과 권한(RBAC)을 분기한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 사용자 식별자 |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE | 로그인 이메일. 계정 식별 기준 |
| `password` | VARCHAR(255) | NOT NULL | 비밀번호 BCrypt 해시. 평문 저장 금지 |
| `name` | VARCHAR(255) | NOT NULL | 사용자 이름(표시명) |
| `role` | VARCHAR(16) | NOT NULL, CHECK | 역할. `EMPLOYEE` \| `EMPLOYER` |
| `workplace_id` | BIGINT | FK→workplace, NULL | 소속 매장. 가입 승인 전이거나 미소속이면 NULL |
| `hourly_wage` | INTEGER | NOT NULL, 기본 0 | 시급(원). 인건비 집계의 기준값 |
| `last_read_notice_at` | TIMESTAMPTZ | NULL | 공지 탭을 마지막으로 확인한 시각. 이보다 최신 공지가 있으면 레드 닷 표시. 신규/미확인이면 NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 계정 생성 시각 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 정보 수정 시각 |

> **레드 닷 로직**: `EXISTS(SELECT 1 FROM notice WHERE workplace_id=:wp AND created_at > COALESCE(:lastReadNoticeAt, '-infinity'))` → 참이면 미확인 공지 있음. 사용자가 공지 탭에 진입하면 `last_read_notice_at = now()`로 갱신.

### 2.3 `notification_setting` — 알림 설정

사용자별 알림 카테고리 on/off. `app_user`와 1:1 (user_id가 PK이자 FK). 내 정보 탭의 "알림 설정" 화면에 대응한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `user_id` | BIGINT | PK, FK→app_user | 대상 사용자. 사용자당 한 행 |
| `swap_enabled` | BOOLEAN | NOT NULL, 기본 TRUE | 대타 관련 알림 수신 여부 |
| `notice_enabled` | BOOLEAN | NOT NULL, 기본 TRUE | 공지 알림 수신 여부 |
| `attendance_enabled` | BOOLEAN | NOT NULL, 기본 TRUE | 근태(출근/지각/결근) 알림 수신 여부 |
| `join_request_enabled` | BOOLEAN | NOT NULL, 기본 TRUE | 가입 신청 알림 수신 여부 (주로 사장) |

### 2.4 `device_token` — FCM 디바이스 토큰

FCM 푸시 발송 대상 토큰. 한 사용자가 여러 기기를 쓸 수 있으므로 1:N. 로그아웃·기기 변경 시 토큰을 삭제·교체한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 토큰 레코드 식별자 |
| `user_id` | BIGINT | FK→app_user, NOT NULL | 토큰 소유 사용자 |
| `token` | VARCHAR(512) | NOT NULL, UNIQUE | FCM 등록 토큰. 기기마다 고유 |
| `platform` | VARCHAR(16) | NOT NULL, 기본 'ANDROID', CHECK | 플랫폼. `ANDROID` \| `IOS` |
| `updated_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 토큰 갱신 시각 |

### 2.5 `shift` — 근무

직원 한 명의 단일 근무 일정. **사람 단위 행**이므로, 같은 시간대에 N명이 일하면 행도 N개 생긴다. 출근 체크와 인건비 집계의 기준 데이터.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 근무 식별자 |
| `workplace_id` | BIGINT | FK→workplace, NOT NULL | 근무가 속한 매장 |
| `employee_id` | BIGINT | FK→app_user, NOT NULL | 근무자. 대타 승인 시 이 값이 대타자로 변경됨 |
| `work_date` | DATE | NOT NULL | 근무 날짜 |
| `start_time` | TIME | NOT NULL | 근무 시작 시각 |
| `end_time` | TIME | NOT NULL | 근무 종료 시각. 야간 교대 시 `end_time < start_time` 가능(앱단에서 익일로 해석) |
| `checked_in_at` | TIMESTAMPTZ | NULL | 실제 출근(QR 체크) 시각. NULL이면 미출근. 진실의 출처 |
| `attendance_status` | VARCHAR(16) | NOT NULL, 기본 'SCHEDULED', CHECK | 근태 상태(비정규화, 조회용). `SCHEDULED`(예정) \| `PRESENT`(정상출근) \| `LATE`(지각) \| `ABSENT`(결근) |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 근무 편성 시각 |
| `updated_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 근무 수정 시각 |

> `attendance_status`는 `checked_in_at`과 `start_time` 비교로 결정되는 파생 상태다. 조회 성능을 위해 컬럼으로 비정규화하되, 갱신 책임은 출근 체크 로직과 결근 판정 배치가 진다.

### 2.6 `swap_request` — 대타 요청

특정 근무(`shift_id`)를 넘기려는 요청. 요청자(`requester_id`)가 생성하고, 사장이 지원자 중 한 명을 골라 승인하면 확정 대타(`substitute_id`)가 채워진다. PTManager의 1급 기능.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 대타 요청 식별자 |
| `workplace_id` | BIGINT | FK→workplace, NOT NULL | 매장. `shift`에서 파생 가능하나, "열린 대타 목록" 조회를 join 없이 처리하려 비정규화로 유지 |
| `shift_id` | BIGINT | FK→shift, NOT NULL | 대타 대상 근무. 어느 근무를 넘기는지 명시. 같은 시간대 다른 근무자와 구분됨 |
| `requester_id` | BIGINT | FK→app_user, NOT NULL | 대타를 요청한 원 근무자. **승인 후에도 절대 변경하지 않음**(이력 보존) |
| `substitute_id` | BIGINT | FK→app_user, NULL | 확정된 대타자 캐시. 승인 전 NULL, 승인 시 선택된 지원자로 채움 |
| `reason` | VARCHAR(500) | NOT NULL | 대타 사유 |
| `status` | VARCHAR(16) | NOT NULL, 기본 'PENDING', CHECK | 상태. `PENDING`(대기) \| `APPROVED`(승인) \| `REJECTED`(거절) |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 요청 생성 시각 |

### 2.7 `swap_application` — 대타 지원

하나의 대타 요청에 대한 개별 직원의 지원. 한 요청에 여러 지원이 달릴 수 있어(1:N), 사장이 이 목록에서 한 명을 승인한다. `(swap_request_id, applicant_id)` 유니크로 중복 지원을 차단.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 지원 식별자 |
| `swap_request_id` | BIGINT | FK→swap_request, NOT NULL | 지원 대상 대타 요청 |
| `applicant_id` | BIGINT | FK→app_user, NOT NULL | 지원한 직원 |
| `status` | VARCHAR(16) | NOT NULL, 기본 'PENDING', CHECK | 지원 상태. `PENDING` \| `APPROVED` \| `REJECTED`. 한 요청에서 한 명만 APPROVED |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 지원 시각 |

### 2.8 `join_request` — 매장 가입 신청

직원이 초대 코드로 매장에 가입 신청한 기록. 사장이 승인하면 해당 `app_user.workplace_id`가 채워진다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 가입 신청 식별자 |
| `workplace_id` | BIGINT | FK→workplace, NOT NULL | 신청 대상 매장 |
| `user_id` | BIGINT | FK→app_user, NOT NULL | 신청한 사용자 |
| `status` | VARCHAR(16) | NOT NULL, 기본 'PENDING', CHECK | 상태. `PENDING` \| `APPROVED` \| `REJECTED` |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 신청 시각 |

### 2.9 `notice` — 공지

매장 단위 공지. 사장(또는 권한자)이 작성하며 단톡방을 일부 대체한다. 게시물(콘텐츠) 성격이며, 알림(`notification`)과는 별개다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 공지 식별자 |
| `workplace_id` | BIGINT | FK→workplace, NOT NULL | 공지가 속한 매장 |
| `author_id` | BIGINT | FK→app_user, NOT NULL | 작성자 |
| `title` | VARCHAR(255) | NOT NULL | 공지 제목 |
| `body` | TEXT | NOT NULL | 공지 본문 |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 작성 시각. 레드 닷 판정의 기준 |

### 2.10 `notice_attachment` — 공지 첨부

공지에 붙는 이미지 등 첨부 파일(S3 저장). 공지 하나에 여러 첨부 가능(1:N).

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 첨부 식별자 |
| `notice_id` | BIGINT | FK→notice, NOT NULL | 소속 공지. 공지 삭제 시 함께 삭제(CASCADE) |
| `file_url` | VARCHAR(512) | NOT NULL | S3에 업로드된 파일 URL |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 업로드 시각 |

### 2.11 `notification` — 알림 (인박스)

사용자에게 전달된 인앱 알림 내역. 시스템이 도메인 이벤트(공지 등록, 대타 결과, 근태 이상, 가입 신청 등)에 따라 자동 생성하는 신호이며, 공지 글(`notice`)과는 역할이 다르다. FCM 푸시와 별개로 앱의 알림 인박스에 쌓이는 영구 기록이다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, 자동증가 | 알림 식별자 |
| `user_id` | BIGINT | FK→app_user, NOT NULL | 알림 수신자 |
| `type` | VARCHAR(24) | NOT NULL, CHECK | 알림 종류. `JOIN_REQUEST`(가입신청) \| `SWAP_REQUEST`(대타요청) \| `SWAP_APPLICATION`(대타지원) \| `SWAP_RESULT`(대타결과) \| `ATTENDANCE`(근태, 지각·결근 포함) \| `SCHEDULE_CHANGED`(근무편성변경) \| `NOTICE`(새공지) |
| `message` | VARCHAR(255) | NOT NULL | 알림 표시 문구 |
| `target_type` | VARCHAR(24) | NULL | 딥링크 대상 종류 (예: 'NOTICE', 'SWAP_REQUEST'). 탭 시 해당 화면 진입 |
| `target_id` | BIGINT | NULL | 딥링크 대상 PK (예: `notice.id`) |
| `is_read` | BOOLEAN | NOT NULL, 기본 FALSE | 읽음 여부. 안 읽은 알림 배지에 사용 |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 알림 발생 시각 |

---

## 3. 테이블 간 관계

| 관계 | 종류 | FK 컬럼 | 설명 |
| --- | --- | --- | --- |
| Workplace → AppUser | 1 : N | `app_user.workplace_id` | 한 매장에 여러 직원·사장이 소속. 미소속(NULL) 허용 |
| Workplace → Shift | 1 : N | `shift.workplace_id` | 매장이 보유하는 근무들 |
| Workplace → SwapRequest | 1 : N | `swap_request.workplace_id` | 매장 단위 대타 요청 모음(조회용 비정규화) |
| Workplace → JoinRequest | 1 : N | `join_request.workplace_id` | 매장으로 들어온 가입 신청들 |
| Workplace → Notice | 1 : N | `notice.workplace_id` | 매장 공지들 |
| AppUser → Shift (employee) | 1 : N | `shift.employee_id` | 직원이 수행하는 근무. 같은 시간대 동시 근무는 별도 행으로 표현 |
| AppUser → SwapRequest (requester) | 1 : N | `swap_request.requester_id` | 대타를 요청한 사람. 이력으로 영구 보존 |
| AppUser → SwapRequest (substitute) | 0..1 : N | `swap_request.substitute_id` | 확정 대타자. 승인 전엔 NULL이므로 0..1 |
| Shift → SwapRequest | 1 : N | `swap_request.shift_id` | 어느 근무를 넘기는지. 한 근무에 PENDING 요청은 최대 1건(부분 유니크) |
| SwapRequest → SwapApplication | 1 : N | `swap_application.swap_request_id` | 한 요청에 여러 직원이 지원 |
| AppUser → SwapApplication (applicant) | 1 : N | `swap_application.applicant_id` | 대타에 지원한 사람 |
| AppUser → JoinRequest | 1 : N | `join_request.user_id` | 가입 신청자 |
| AppUser → Notice (author) | 1 : N | `notice.author_id` | 공지 작성자 |
| Notice → NoticeAttachment | 1 : N | `notice_attachment.notice_id` | 공지의 첨부 파일들 |
| AppUser → Notification | 1 : N | `notification.user_id` | 알림 수신자 |
| AppUser → DeviceToken | 1 : N | `device_token.user_id` | 사용자의 FCM 토큰들(멀티 디바이스) |
| AppUser → NotificationSetting | 1 : 1 | `notification_setting.user_id` | 사용자별 알림 설정 |

> 공지 읽음(누가 읽었는지)은 테이블 관계로 표현하지 않고, `app_user.last_read_notice_at`와 `notice.created_at` 비교로 "미확인 공지 존재 여부"만 계산한다.

**핵심 흐름 요약**

- 대타: `SHIFT` ─(대상근무)→ `SWAP_REQUEST` ─(지원받음)→ `SWAP_APPLICATION` ←(지원)─ `APP_USER`. 승인 시 `swap_request.substitute_id`와 `shift.employee_id`가 갱신된다.
- 공지: `NOTICE`가 `NOTICE_ATTACHMENT`(첨부)를 거느린다. 읽음 표시는 `app_user.last_read_notice_at`로 처리.
- 알림: 도메인 이벤트 발생 → `NOTIFICATION` 적재 + `DEVICE_TOKEN`으로 FCM 발송. `NOTIFICATION_SETTING`이 발송 여부를 거른다.

---

## 4. SQL DDL (PostgreSQL)

```sql
CREATE TABLE workplace (
    id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(255),
    invite_code VARCHAR(16)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password            VARCHAR(255) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    role                VARCHAR(16)  NOT NULL CHECK (role IN ('EMPLOYEE', 'EMPLOYER')),
    workplace_id        BIGINT       REFERENCES workplace(id),
    hourly_wage         INTEGER      NOT NULL DEFAULT 0,
    last_read_notice_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notification_setting (
    user_id              BIGINT PRIMARY KEY REFERENCES app_user(id),
    swap_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    notice_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    attendance_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    join_request_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE device_token (
    id         BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id    BIGINT       NOT NULL REFERENCES app_user(id),
    token      VARCHAR(512) NOT NULL UNIQUE,
    platform   VARCHAR(16)  NOT NULL DEFAULT 'ANDROID'
               CHECK (platform IN ('ANDROID', 'IOS')),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE shift (
    id                BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    workplace_id      BIGINT  NOT NULL REFERENCES workplace(id),
    employee_id       BIGINT  NOT NULL REFERENCES app_user(id),
    work_date         DATE    NOT NULL,
    start_time        TIME    NOT NULL,
    end_time          TIME    NOT NULL,
    checked_in_at     TIMESTAMPTZ,
    attendance_status VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED'
                      CHECK (attendance_status IN ('SCHEDULED','PRESENT','LATE','ABSENT')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE swap_request (
    id            BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    workplace_id  BIGINT       NOT NULL REFERENCES workplace(id),
    shift_id      BIGINT       NOT NULL REFERENCES shift(id) ON DELETE RESTRICT,
    requester_id  BIGINT       NOT NULL REFERENCES app_user(id),
    substitute_id BIGINT           NULL REFERENCES app_user(id),
    reason        VARCHAR(500) NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE swap_application (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    swap_request_id BIGINT      NOT NULL REFERENCES swap_request(id) ON DELETE CASCADE,
    applicant_id    BIGINT      NOT NULL REFERENCES app_user(id),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (swap_request_id, applicant_id)
);

CREATE TABLE join_request (
    id           BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    workplace_id BIGINT      NOT NULL REFERENCES workplace(id),
    user_id      BIGINT      NOT NULL REFERENCES app_user(id),
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notice (
    id           BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    workplace_id BIGINT       NOT NULL REFERENCES workplace(id),
    author_id    BIGINT       NOT NULL REFERENCES app_user(id),
    title        VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notice_attachment (
    id         BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    notice_id  BIGINT       NOT NULL REFERENCES notice(id) ON DELETE CASCADE,
    file_url   VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notification (
    id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT      NOT NULL REFERENCES app_user(id),
    type        VARCHAR(24) NOT NULL
                CHECK (type IN ('JOIN_REQUEST','SWAP_REQUEST','SWAP_APPLICATION',
                                'SWAP_RESULT','ATTENDANCE','SCHEDULE_CHANGED','NOTICE')),
    message     VARCHAR(255) NOT NULL,
    target_type VARCHAR(24),
    target_id   BIGINT,
    is_read     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 5. 인덱스

> PostgreSQL은 PK·UNIQUE 제약에만 인덱스를 자동 생성한다. **FK 컬럼은 자동 생성되지 않으므로**, 조회·조인에 쓰는 FK는 직접 인덱스를 만든다.

```sql
-- 사장 홈 "오늘 출근 현황", 스케줄 캘린더 (등치 → 범위 순서)
CREATE INDEX idx_shift_wp_date ON shift(workplace_id, work_date);

-- "열린 대타 목록" / 사장 승인 대기열 (PENDING만 조회 → 부분 인덱스)
CREATE INDEX idx_swap_pending ON swap_request(workplace_id) WHERE status = 'PENDING';

-- 한 근무에 PENDING 대타 요청 최대 1건 보장 (무결성)
CREATE UNIQUE INDEX uq_open_swap ON swap_request(shift_id) WHERE status = 'PENDING';

-- 안 읽은 알림 배지 (안 읽은 건 소수 → 부분 인덱스)
CREATE INDEX idx_notif_unread ON notification(user_id) WHERE is_read = FALSE;

-- 레드 닷 판정: 매장의 최신 공지 조회
CREATE INDEX idx_notice_wp_created ON notice(workplace_id, created_at);

-- 조인용 FK 인덱스
CREATE INDEX idx_shift_employee ON shift(employee_id);
CREATE INDEX idx_swap_shift     ON swap_request(shift_id);
```

> "지원자 목록(`WHERE swap_request_id=?`)" 조회는 `swap_application`의 `UNIQUE(swap_request_id, applicant_id)`가 선두 컬럼으로 이미 커버하므로 별도 인덱스가 필요 없다.

---

## 6. 애플리케이션 레벨 검증 규칙

DB 제약만으로 표현하지 않고 서비스 코드에서 강제하는 규칙. (성능·복잡도를 고려해 스키마 대신 앱단에서 처리)

1. **대타 요청 생성 시** — `shift.employee_id == requester_id`인지 확인(본인 근무만 대타 가능).
2. **대타 지원 시** — 요청자 본인이 자기 요청에 지원하는 것 차단.
3. **대타자 시간 중복 방지(더블부킹)** — 지원·승인 시점에, 해당 직원에게 같은 `work_date`이고 시간 구간 `[start_time, end_time]`이 겹치는 SHIFT가 이미 있으면 지원 불가/승인 거부. 야간 교대(`end_time < start_time`)는 익일로 보정해 비교.
4. **대타 승인** — 단일 트랜잭션으로 처리하며, 동시성 가드를 둔다. 이어서 ① 선택 지원 `APPROVED`·나머지 `REJECTED`, ② `shift.employee_id`를 대타자로 재배정, ③ 결과 알림 생성을 같은 트랜잭션 안에서 수행한다. `requester_id`는 갱신하지 않는다(이력 보존).

   ```sql
   UPDATE swap_request SET status='APPROVED', substitute_id=:applicantId
   WHERE id=:reqId AND status='PENDING';   -- 영향 행 0이면 이미 처리됨 → 롤백
   ```

5. **근태 상태 갱신** — 출근 체크 시 `checked_in_at` 기록 후 `start_time` 대비 `PRESENT`/`LATE` 판정. 근무 종료 후에도 미출근이면 배치가 `ABSENT`로 전이.
6. **공지 읽음(레드 닷)** — 사용자가 공지 탭에 진입하면 `app_user.last_read_notice_at = now()`로 갱신. 미확인 공지 여부는 매장 최신 공지의 `created_at`과 비교해 판정.

---

## 부록: 운영 메모

- 근무 삭제 정책은 `swap_request.shift_id`에 `ON DELETE RESTRICT`를 걸어, 대타 요청이 걸린 근무의 임의 삭제를 막는다. 근무 삭제 기능을 제공할 경우 열린 대타를 먼저 정리하는 로직이 선행되어야 한다.
- 공지 삭제 시 첨부(`notice_attachment`)는 `ON DELETE CASCADE`로 함께 정리된다.
- 인건비 집계는 별도 테이블 없이 `shift`(근무 시간) × `app_user.hourly_wage`(시급)를 조회 시점에 계산한다. 결근(`ABSENT`)은 집계에서 제외하며, 야간 교대 시간 보정은 서비스단에서 처리한다.

---

> **참고:** 본 문서는 PTManager의 *목표(target) 데이터 모델*이다. 현재 백엔드 코드(`com.ptmanager.backend.domain`)는 MVP 초기 단계로 일부 테이블·컬럼(`swap_application`, `notification_setting`, `device_token`, `notice_attachment`, `shift.checked_in_at`/`attendance_status`, `notification.target_*` 등)을 아직 구현하지 않았다. 단계별 구현은 [API_SPEC.md](API_SPEC.md) 및 제안서의 향후 계획을 따른다.
