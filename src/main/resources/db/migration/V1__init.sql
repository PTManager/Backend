-- PTManager 초기 스키마 (PostgreSQL). ERD.md 기준 11개 테이블 + 인덱스.
-- 운영(prod) 프로파일에서 Flyway가 실행한다. (로컬/테스트는 H2 + Hibernate create-drop)

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

-- 인덱스 ----------------------------------------------------------------------

-- 사장 홈 "오늘 출근 현황", 스케줄 캘린더
CREATE INDEX idx_shift_wp_date ON shift(workplace_id, work_date);
CREATE INDEX idx_shift_employee ON shift(employee_id);

-- "열린 대타 목록" / 사장 승인 대기열 (PENDING만 조회 → 부분 인덱스)
CREATE INDEX idx_swap_pending ON swap_request(workplace_id) WHERE status = 'PENDING';
-- 한 근무에 PENDING 대타 요청 최대 1건 보장 (부분 유니크)
CREATE UNIQUE INDEX uq_open_swap ON swap_request(shift_id) WHERE status = 'PENDING';
CREATE INDEX idx_swap_shift ON swap_request(shift_id);

-- 안 읽은 알림 배지 (부분 인덱스)
CREATE INDEX idx_notif_unread ON notification(user_id) WHERE is_read = FALSE;

-- 레드 닷 판정: 매장의 최신 공지 조회
CREATE INDEX idx_notice_wp_created ON notice(workplace_id, created_at);
