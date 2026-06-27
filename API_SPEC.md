# PTManager API 명세서

알바생·사장님을 연결하는 근무 스케줄·대타·근태 관리 앱 **PTManager**의 REST API 명세서.

- **구성**: 직원용 앱(PTManagerEmployee) · 사장용 앱(PTManagerEmployer) · 공통 백엔드 서버(3-tier)
- **인증**: Spring Security + JWT (Bearer). 역할(EMPLOYEE/EMPLOYER) 기반 RBAC
- **DB**: PostgreSQL 11개 테이블 (`workplace`, `app_user`, `notification_setting`, `device_token`, `shift`, `swap_request`, `swap_application`, `join_request`, `notice`, `notice_attachment`, `notification`) — [ERD.md](ERD.md) 참고
- **시각 표기**: 모든 타임스탬프는 ISO-8601 UTC(`TIMESTAMPTZ`)

도메인 컨트롤러는 Auth · Workplace · User · Shift · SwapRequest · Notice · Notification · Payroll · Health 로 분리되며, 공통 `ApiExceptionHandler`로 일관된 에러 응답을 제공한다.

> OpenAPI 3.0.3 / 총 **43개 엔드포인트**. 본 문서는 Swagger(SpringDoc) 명세와 동기화되는 기준 문서이며, 서버·클라이언트가 이 명세를 기준으로 병렬 개발한다.

---

## 공통 사항

| 항목 | 값 |
| --- | --- |
| Base URL (로컬) | `http://localhost:8080` (에뮬레이터에서는 `http://10.0.2.2:8080`) |
| Base URL (운영) | `https://api.ptmanager.app` (AWS EC2 + RDS) |
| 포맷 | `application/json` (요청·응답). 첨부 업로드만 `multipart/form-data` |
| 인증 헤더 | `Authorization: Bearer {accessToken}` |
| 날짜/시간 | `date` = `YYYY-MM-DD`, `time` = `HH:mm:ss`, `date-time` = ISO-8601 UTC |

### 인증 (JWT)

- `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `GET /api/health`를 제외한 모든 엔드포인트는 액세스 토큰이 필요하다.
- 로그인 시 액세스/리프레시 토큰을 발급하고, 액세스 토큰 만료 시 `POST /api/auth/refresh`로 갱신한다.
- 응답의 `user.role`에 따라 클라이언트가 직원/사장 앱 경험으로 분기한다. 사장 전용 API(매장 생성, 근무 편성, 대타 승인, 인건비 등)는 서버에서 RBAC로 강제한다.

### 공통 에러 응답

모든 에러는 공통 `ApiExceptionHandler`가 동일한 `ApiError` 구조로 반환한다.

```json
{
  "timestamp": "2026-06-27T09:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "SWAP_ALREADY_PROCESSED",
  "message": "이미 처리된 대타 요청입니다.",
  "path": "/api/swap-requests/200/approve"
}
```

| HTTP | 의미 | 대표 상황 |
| --- | --- | --- |
| 400 | 잘못된 요청 / 검증 실패 | 본문 유효성 실패, 유효하지 않은 QR 토큰, 더블부킹 |
| 401 | 인증 필요 / 토큰 만료 | 토큰 누락·만료·무효 |
| 403 | 권한 없음 | 역할 불일치(RBAC), 본인 근무가 아님 |
| 404 | 리소스 없음 | 사용자/매장/근무/요청/공지/알림 미존재, 잘못된 초대 코드 |
| 409 | 충돌 | 이메일 중복, 중복 신청·지원, 이미 처리됨(동시성), 대타 걸린 근무 삭제 |
| 413 | 용량 초과 | 첨부 파일 용량 초과 |

---

## 1. Auth — 회원가입·로그인·토큰·내 계정

### 1.1 회원가입 — `POST /api/auth/signup` 🔓

이메일·비밀번호·이름·역할로 계정을 생성한다. 가입 직후 `workplaceId`는 NULL이며, 직원은 초대 코드로 매장 가입 신청을 별도 진행한다. 비밀번호는 BCrypt로 해시 저장된다.

요청 (`SignupRequest`)

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string(email) | ✅ | 로그인 이메일 |
| `password` | string | ✅ | 최소 8자 |
| `name` | string | ✅ | 표시명 |
| `role` | `Role` | ✅ | `EMPLOYEE` \| `EMPLOYER` |

```json
{ "email": "jihun@example.com", "password": "P@ssw0rd!", "name": "지훈", "role": "EMPLOYEE" }
```

응답 `201 Created` — `TokenResponse`. 오류: `400`, `409`(이미 사용 중인 이메일).

### 1.2 로그인 — `POST /api/auth/login` 🔓

이메일·비밀번호 검증 후 JWT 액세스/리프레시 토큰을 발급한다.

요청 (`LoginRequest`) `{ "email": "jihun@example.com", "password": "P@ssw0rd!" }`

응답 `200 OK` — `TokenResponse`. 오류: `401`(이메일/비밀번호 불일치).

### 1.3 액세스 토큰 갱신 — `POST /api/auth/refresh` 🔓

리프레시 토큰으로 새 액세스 토큰을 발급한다.

요청 (`RefreshRequest`) `{ "refreshToken": "eyJ..." }`

응답 `200 OK` — `TokenResponse`. 오류: `401`(유효하지 않거나 만료된 리프레시 토큰).

### 1.4 로그아웃 — `POST /api/auth/logout`

현재 기기의 FCM 디바이스 토큰을 제거하고 세션을 종료한다.

요청 (`LogoutRequest`, 선택) `{ "deviceToken": "fGc...:APA91b..." }`

응답 `204 No Content`. 오류: `401`.

### 1.5 내 계정 정보 조회 — `GET /api/auth/me`

JWT로 식별된 현재 사용자 정보(소속 매장·역할 포함)를 반환한다.

응답 `200 OK` — `User`. 오류: `401`.

---

## 2. Workplace — 매장·멤버·가입 신청

### 2.1 매장 생성 (사장) — `POST /api/workplaces`

EMPLOYER가 매장을 생성한다. 생성 시 직원 가입용 `inviteCode`가 자동 발급되고, 생성자는 해당 매장에 소속된다.

요청 (`CreateWorkplaceRequest`)

| 필드 | 타입 | 필수 |
| --- | --- | --- |
| `name` | string | ✅ |
| `address` | string | ❌ |

```json
{ "name": "시루 카페 정왕점", "address": "경기도 시흥시 정왕동 123-4" }
```

응답 `201 Created` — `Workplace`. 오류: `400`, `403`.

### 2.2 매장 정보 조회 — `GET /api/workplaces/{workplaceId}`

응답 `200 OK` — `Workplace`. 오류: `404`.

### 2.3 매장 멤버 목록 — `GET /api/workplaces/{workplaceId}/members`

매장에 소속된 직원·사장 목록을 반환한다. 내 정보 탭 '매장 멤버 목록' 화면에 대응.

쿼리: `role`(선택, `Role` 필터).

응답 `200 OK` — `User[]`. 오류: `404`.

### 2.4 매장 가입 신청 (직원) — `POST /api/join-requests`

직원이 초대 코드로 매장 가입을 신청한다. 상태는 `PENDING`으로 생성되며, 사장 승인 시 `app_user.workplace_id`가 채워진다.

요청 (`CreateJoinRequest`) `{ "inviteCode": "A1B2C3D4" }`

응답 `201 Created` — `JoinRequest`. 오류: `404`(유효하지 않은 초대 코드), `409`(이미 처리 대기 중인 신청 존재).

### 2.5 가입 신청 목록 (사장) — `GET /api/join-requests`

사장이 자기 매장으로 들어온 가입 신청을 조회한다. 홈 탭 '처리 대기' 항목에 대응.

쿼리: `workplaceId`(필수), `status`(`RequestStatus` 필터, 기본 `PENDING`).

응답 `200 OK` — `JoinRequest[]`. 오류: `403`.

### 2.6 가입 신청 승인/거절 (사장) — `PATCH /api/join-requests/{joinRequestId}`

`APPROVE` 시 신청자의 `workplaceId`가 채워진다. 결과 알림(`JOIN_REQUEST`)이 신청자에게 생성된다.

요청 (`DecisionRequest`) `{ "decision": "APPROVE" }` (`APPROVE` \| `REJECT`)

응답 `200 OK` — `JoinRequest`. 오류: `403`, `404`, `409`(이미 처리됨).

---

## 3. User — 프로필·알림 설정·디바이스 토큰

### 3.1 프로필 수정 — `PATCH /api/users/me`

이름 등 표시 정보를 수정한다.

요청 (`UpdateProfileRequest`) `{ "name": "지훈" }`

응답 `200 OK` — `User`. 오류: `401`.

### 3.2 알림 설정 조회 — `GET /api/users/me/notification-setting`

응답 `200 OK` — `NotificationSetting`. 오류: `401`.

### 3.3 알림 설정 수정 — `PATCH /api/users/me/notification-setting`

대타·공지·근태·가입 신청 알림 카테고리별 on/off를 갱신한다.

요청 (`NotificationSettingUpdate`) — `swapEnabled`, `noticeEnabled`, `attendanceEnabled`, `joinRequestEnabled`(모두 boolean, 선택).

응답 `200 OK` — `NotificationSetting`. 오류: `401`.

### 3.4 FCM 디바이스 토큰 등록 — `POST /api/users/me/device-tokens`

푸시 알림을 받을 기기 토큰을 등록한다. 멀티 디바이스를 지원하며, 동일 토큰 재등록 시 갱신(upsert)된다.

요청 (`RegisterDeviceTokenRequest`) `{ "token": "fGc...:APA91b...", "platform": "ANDROID" }`

응답 `201 Created` — `DeviceToken`. 오류: `401`.

### 3.5 디바이스 토큰 삭제 — `DELETE /api/users/me/device-tokens/{token}`

로그아웃·기기 변경 시 해당 토큰을 제거한다.

응답 `204 No Content`. 오류: `401`, `404`.

---

## 4. Shift — 근무 편성·조회·QR 출근

### 4.1 근무 목록 조회 — `GET /api/shifts`

직원은 본인 근무(`employeeId=me`)를, 사장은 매장 전체 근무를 조회한다. 사장 홈 '오늘 출근 현황', 스케줄 캘린더에 대응. `idx_shift_wp_date`로 조회한다.

쿼리: `workplaceId`(사장), `employeeId`(직원 ID 또는 `me`), `from`(date), `to`(date), `status`(`AttendanceStatus`).

응답 `200 OK` — `Shift[]`. 오류: `401`.

### 4.2 근무 편성 (사장) — `POST /api/shifts`

사장이 직원 한 명의 단일 근무를 편성한다. 사람 단위 행이므로 같은 시간대 N명이면 N번 호출한다. 편성 시 대상 직원에게 `SCHEDULE_CHANGED` 알림이 생성될 수 있다.

요청 (`CreateShiftRequest`)

| 필드 | 타입 | 필수 |
| --- | --- | --- |
| `workplaceId` | int64 | ✅ |
| `employeeId` | int64 | ✅ |
| `workDate` | date | ✅ |
| `startTime` | time | ✅ |
| `endTime` | time | ✅ |

```json
{ "workplaceId": 1, "employeeId": 10, "workDate": "2026-06-30", "startTime": "18:00:00", "endTime": "23:00:00" }
```

응답 `201 Created` — `Shift`. 오류: `400`, `403`.

### 4.3 근무 상세 조회 — `GET /api/shifts/{shiftId}`

응답 `200 OK` — `Shift`. 오류: `404`.

### 4.4 근무 수정 (사장) — `PATCH /api/shifts/{shiftId}`

근무 날짜·시간·배정 직원을 수정한다.

요청 (`UpdateShiftRequest`) — `employeeId`, `workDate`, `startTime`, `endTime`(모두 선택).

응답 `200 OK` — `Shift`. 오류: `403`, `404`.

### 4.5 근무 삭제 (사장) — `DELETE /api/shifts/{shiftId}`

근무를 삭제한다. 단, PENDING 대타 요청이 걸린 근무는 `ON DELETE RESTRICT`로 삭제가 거부된다(409). 열린 대타를 먼저 정리해야 한다.

응답 `204 No Content`. 오류: `403`, `404`, `409`(대타 요청이 걸려 삭제 불가).

### 4.6 QR 출근 체크 (직원) — `POST /api/shifts/{shiftId}/check-in`

매장 QR 스캔으로 출근을 기록한다. 별도 하드웨어 없이 위치/시점 인증을 대신한다. `checkedInAt`을 기록한 뒤 `startTime` 대비 `PRESENT`/`LATE`를 판정해 `attendanceStatus`를 갱신한다. 출근 기록이 인건비 집계의 '진실의 출처'다.

요청 (`CheckInRequest`) `{ "qrToken": "wp1:1719740400:9f8a..." }`

응답 `200 OK` — `Shift`. 오류: `400`(유효하지 않은 QR/출근 가능 시간대 아님), `403`(본인 근무 아님), `409`(이미 출근 처리됨).

---

## 5. SwapRequest — 대타 요청–지원–승인 (1급 기능)

### 5.1 대타 요청 생성 (직원) — `POST /api/swap-requests`

본인 근무를 넘기기 위한 대타 요청을 생성한다. 상태는 `PENDING`으로 시작. **앱단 검증**: `shift.employeeId == 요청자`여야 하며(본인 근무만), 한 근무에 PENDING 요청은 부분 유니크로 최대 1건만 허용된다. 같은 매장 직원들에게 `SWAP_REQUEST` 알림이 발송된다.

요청 (`CreateSwapRequest`)

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `shiftId` | int64 | ✅ | 넘기려는 본인 근무 |
| `reason` | string | ✅ | 최대 500자 |

```json
{ "shiftId": 100, "reason": "갑자기 일이 생겨 대타 부탁드립니다." }
```

응답 `201 Created` — `SwapRequest`. 오류: `403`(본인 근무 아님), `409`(이미 열린 PENDING 요청 존재).

### 5.2 대타 요청 목록 조회 — `GET /api/swap-requests`

조회 관점을 `view`로 구분한다. `idx_swap_pending` 부분 인덱스로 조회.

- `open`: 직원이 지원 가능한 열린 대타 목록 (PENDING, 본인 요청 제외)
- `mine`: 내가 요청한 대타 내역
- `pending`: 사장 승인 대기열

쿼리: `workplaceId`(필수), `view`(필수, `open`\|`mine`\|`pending`), `status`(`RequestStatus`, mine 탭 구분).

응답 `200 OK` — `SwapRequest[]`. 오류: `401`.

### 5.3 대타 요청 상세 (지원자 목록 포함) — `GET /api/swap-requests/{swapRequestId}`

대상 근무 정보와 지원자 목록(`swap_application`)을 함께 반환한다. 사장 '요청 상세' 화면에 대응.

응답 `200 OK` — `SwapRequestDetail`. 오류: `404`.

### 5.4 대타 지원 (직원) — `POST /api/swap-requests/{swapRequestId}/applications`

열린 대타 요청에 지원한다. **앱단 검증**: 요청자 본인은 지원 불가, 같은 `work_date`에 시간이 겹치는 근무가 있으면(더블부킹) 거부. `(swap_request_id, applicant_id)` 유니크로 중복 지원 차단.

응답 `201 Created` — `SwapApplication`. 오류: `400`(더블부킹), `403`(본인 요청), `409`(이미 지원 / 마감된 요청).

### 5.5 지원자 목록 조회 (사장) — `GET /api/swap-requests/{swapRequestId}/applications`

응답 `200 OK` — `SwapApplication[]`. 오류: `404`.

### 5.6 대타 승인 + 대체 근무자 지정 (사장) — `POST /api/swap-requests/{swapRequestId}/approve`

지원자 중 한 명을 선택해 승인한다. **단일 트랜잭션·동시성 가드**로 처리:

1. `UPDATE ... SET status='APPROVED', substitute_id=:applicantId WHERE id=:reqId AND status='PENDING'` (영향 행 0이면 이미 처리됨 → 롤백)
2. 선택 지원 APPROVED, 나머지 지원 REJECTED
3. `shift.employee_id`를 대타자로 재배정
4. 결과 알림(`SWAP_RESULT`) 생성

`requesterId`는 이력 보존을 위해 절대 변경하지 않는다.

요청 (`ApproveSwapRequest`) `{ "applicantId": 11 }`

응답 `200 OK` — `SwapRequest`. 오류: `403`, `404`, `409`(이미 처리됨 / 동시성 충돌).

### 5.7 대타 거절 (사장) — `POST /api/swap-requests/{swapRequestId}/reject`

요청을 REJECTED로 전이한다. 요청자에게 결과 알림(`SWAP_RESULT`)이 생성된다.

응답 `200 OK` — `SwapRequest`. 오류: `403`, `404`, `409`(이미 처리됨).

### 5.8 내가 지원한 대타 내역 (직원) — `GET /api/swap-applications/me`

직원 대타 탭의 '내가 지원한' 탭에 대응. 상태별 필터링 가능.

쿼리: `status`(`RequestStatus`).

응답 `200 OK` — `SwapApplication[]`. 오류: `401`.

---

## 6. Notice — 공지·첨부·레드 닷

### 6.1 공지 피드 조회 — `GET /api/notices`

매장 공지 목록을 최신순으로 반환(페이지네이션). 공지 탭 진입 시 클라이언트는 `/api/notices/read`를 함께 호출한다.

쿼리: `workplaceId`(필수), `page`(기본 0), `size`(기본 20, 최대 100).

응답 `200 OK` — `NoticePage`. 오류: `401`.

### 6.2 공지 작성 (사장) — `POST /api/notices`

매장 공지를 작성한다. 첨부는 먼저 업로드해 받은 `fileUrl` 목록을 함께 전달한다. 작성 시 매장 직원들에게 `NOTICE` 알림이 발송된다.

요청 (`CreateNoticeRequest`)

| 필드 | 타입 | 필수 |
| --- | --- | --- |
| `workplaceId` | int64 | ✅ |
| `title` | string | ✅ |
| `body` | string | ✅ |
| `attachmentUrls` | string(uri)[] | ❌ |

응답 `201 Created` — `Notice`. 오류: `403`.

### 6.3 공지 상세 조회 — `GET /api/notices/{noticeId}`

응답 `200 OK` — `Notice`. 오류: `404`.

### 6.4 공지 삭제 (사장) — `DELETE /api/notices/{noticeId}`

공지를 삭제한다. 첨부(`notice_attachment`)는 `ON DELETE CASCADE`로 함께 삭제된다.

응답 `204 No Content`. 오류: `403`, `404`.

### 6.5 공지 첨부 파일 업로드 — `POST /api/notices/attachments`

이미지 등 첨부를 S3에 업로드하고 접근 가능한 `fileUrl`을 반환한다. 반환된 URL을 공지 작성 요청에 포함한다.

요청: `multipart/form-data`, `file`(binary, 필수).

응답 `201 Created` — `NoticeAttachment`. 오류: `413`(용량 초과).

### 6.6 미확인 공지 여부 (레드 닷) — `GET /api/notices/unread`

매장 최신 공지의 `created_at`과 사용자 `last_read_notice_at`를 비교해 미확인 공지 존재 여부만 반환한다.

쿼리: `workplaceId`(필수).

응답 `200 OK` — `UnreadFlag` `{ "hasUnread": true }`. 오류: `401`.

### 6.7 공지 읽음 처리 — `POST /api/notices/read`

사용자가 공지 탭에 진입하면 호출한다. `last_read_notice_at = now()`로 갱신해 레드 닷을 해제한다.

응답 `204 No Content`. 오류: `401`.

---

## 7. Notification — 인앱 알림 인박스

### 7.1 알림 인박스 조회 — `GET /api/notifications`

사용자에게 적재된 인앱 알림을 최신순으로 반환(페이지네이션).

쿼리: `isRead`(boolean 필터), `page`(기본 0), `size`(기본 20).

응답 `200 OK` — `NotificationPage`. 오류: `401`.

### 7.2 안 읽은 알림 수 (배지) — `GET /api/notifications/unread-count`

`idx_notif_unread` 부분 인덱스로 안 읽은 알림 개수를 반환한다.

응답 `200 OK` — `UnreadCount` `{ "count": 3 }`. 오류: `401`.

### 7.3 알림 읽음 처리 — `PATCH /api/notifications/{notificationId}/read`

응답 `204 No Content`. 오류: `404`.

### 7.4 알림 전체 읽음 처리 — `POST /api/notifications/read-all`

응답 `204 No Content`. 오류: `401`.

---

## 8. Payroll — 인건비 집계 (사장)

### 8.1 인건비 집계 — `GET /api/payroll`

실제 근태 기반으로 인건비를 집계한다. 별도 테이블 없이 `shift`(근무 시간) × `app_user.hourly_wage`(시급)를 조회 시점에 계산한다. 결근(`ABSENT`)은 집계에서 제외하고, 야간 교대 시간은 서비스단에서 보정한다.

쿼리: `workplaceId`(필수), `yearMonth`(필수, `^[0-9]{4}-[0-9]{2}$`, 예: `2026-06`).

응답 `200 OK` — `PayrollSummary`. 오류: `403`.

---

## 9. Health — 서버 헬스 체크

### 9.1 헬스 체크 — `GET /api/health` 🔓

응답 `200 OK` — `HealthStatus` `{ "status": "UP", "timestamp": "..." }`.

---

## 데이터 모델 (스키마)

### 열거형(enum)

| 이름 | 값 |
| --- | --- |
| `Role` | `EMPLOYEE`, `EMPLOYER` |
| `Platform` | `ANDROID`, `IOS` |
| `AttendanceStatus` | `SCHEDULED`, `PRESENT`, `LATE`, `ABSENT` |
| `RequestStatus` | `PENDING`, `APPROVED`, `REJECTED` |
| `NotificationType` | `JOIN_REQUEST`, `SWAP_REQUEST`, `SWAP_APPLICATION`, `SWAP_RESULT`, `ATTENDANCE`, `SCHEDULE_CHANGED`, `NOTICE` |

### 핵심 응답 객체

**Workplace** — `id`, `name`, `address?`, `inviteCode`, `createdAt`

**User** — `id`, `email`, `name`, `role`, `workplaceId?`, `hourlyWage`, `createdAt`, `updatedAt` (비밀번호 미포함)

**NotificationSetting** — `userId`, `swapEnabled`, `noticeEnabled`, `attendanceEnabled`, `joinRequestEnabled`

**DeviceToken** — `id`, `userId`, `token`, `platform`, `updatedAt`

**Shift** — `id`, `workplaceId`, `employeeId`, `employeeName`, `workDate`, `startTime`, `endTime`, `checkedInAt?`, `attendanceStatus`, `createdAt`, `updatedAt`

**SwapRequest** — `id`, `workplaceId`, `shiftId`, `requesterId`, `substituteId?`, `reason`, `status`, `createdAt`

**SwapRequestDetail** — `SwapRequest` + `shift`(Shift) + `applications`(SwapApplication[])

**SwapApplication** — `id`, `swapRequestId`, `applicantId`, `applicantName`, `status`, `createdAt`

**JoinRequest** — `id`, `workplaceId`, `userId`, `userName`, `status`, `createdAt`

**Notice** — `id`, `workplaceId`, `authorId`, `authorName`, `title`, `body`, `attachments`(NoticeAttachment[]), `createdAt`

**NoticeAttachment** — `id`, `noticeId?`, `fileUrl`, `createdAt`

**Notification** — `id`, `userId`, `type`, `message`, `targetType?`, `targetId?`, `isRead`, `createdAt`

**TokenResponse** — `accessToken`, `refreshToken`, `tokenType`(`Bearer`), `expiresIn`(초), `user`(User)

**PayrollSummary** — `workplaceId`, `yearMonth`, `totalAmount`, `items`(PayrollItem[])

**PayrollItem** — `employeeId`, `employeeName`, `hourlyWage`, `workedMinutes`, `amount`

**NoticePage / NotificationPage** — `content[]`, `page`, `size`, `totalElements`, `totalPages`

**UnreadFlag** `{ hasUnread }` · **UnreadCount** `{ count }` · **HealthStatus** `{ status, timestamp }`

**ApiError** — `timestamp`, `status`, `error`, `code`, `message`, `path`

---

## 엔드포인트 요약 (43개)

| # | Method | Path | 태그 | 설명 | 성공 |
| --- | --- | --- | --- | --- | --- |
| 1 | POST | `/api/auth/signup` | Auth | 회원가입 🔓 | 201 |
| 2 | POST | `/api/auth/login` | Auth | 로그인 🔓 | 200 |
| 3 | POST | `/api/auth/refresh` | Auth | 토큰 갱신 🔓 | 200 |
| 4 | POST | `/api/auth/logout` | Auth | 로그아웃 | 204 |
| 5 | GET | `/api/auth/me` | Auth | 내 계정 조회 | 200 |
| 6 | POST | `/api/workplaces` | Workplace | 매장 생성(사장) | 201 |
| 7 | GET | `/api/workplaces/{workplaceId}` | Workplace | 매장 조회 | 200 |
| 8 | GET | `/api/workplaces/{workplaceId}/members` | Workplace | 멤버 목록 | 200 |
| 9 | POST | `/api/join-requests` | Workplace | 가입 신청(직원) | 201 |
| 10 | GET | `/api/join-requests` | Workplace | 가입 신청 목록(사장) | 200 |
| 11 | PATCH | `/api/join-requests/{joinRequestId}` | Workplace | 가입 승인/거절(사장) | 200 |
| 12 | PATCH | `/api/users/me` | User | 프로필 수정 | 200 |
| 13 | GET | `/api/users/me/notification-setting` | User | 알림 설정 조회 | 200 |
| 14 | PATCH | `/api/users/me/notification-setting` | User | 알림 설정 수정 | 200 |
| 15 | POST | `/api/users/me/device-tokens` | User | FCM 토큰 등록 | 201 |
| 16 | DELETE | `/api/users/me/device-tokens/{token}` | User | FCM 토큰 삭제 | 204 |
| 17 | GET | `/api/shifts` | Shift | 근무 목록 | 200 |
| 18 | POST | `/api/shifts` | Shift | 근무 편성(사장) | 201 |
| 19 | GET | `/api/shifts/{shiftId}` | Shift | 근무 상세 | 200 |
| 20 | PATCH | `/api/shifts/{shiftId}` | Shift | 근무 수정(사장) | 200 |
| 21 | DELETE | `/api/shifts/{shiftId}` | Shift | 근무 삭제(사장) | 204 |
| 22 | POST | `/api/shifts/{shiftId}/check-in` | Shift | QR 출근 체크(직원) | 200 |
| 23 | POST | `/api/swap-requests` | SwapRequest | 대타 요청 생성(직원) | 201 |
| 24 | GET | `/api/swap-requests` | SwapRequest | 대타 요청 목록 | 200 |
| 25 | GET | `/api/swap-requests/{swapRequestId}` | SwapRequest | 대타 요청 상세 | 200 |
| 26 | POST | `/api/swap-requests/{swapRequestId}/applications` | SwapRequest | 대타 지원(직원) | 201 |
| 27 | GET | `/api/swap-requests/{swapRequestId}/applications` | SwapRequest | 지원자 목록(사장) | 200 |
| 28 | POST | `/api/swap-requests/{swapRequestId}/approve` | SwapRequest | 대타 승인(사장) | 200 |
| 29 | POST | `/api/swap-requests/{swapRequestId}/reject` | SwapRequest | 대타 거절(사장) | 200 |
| 30 | GET | `/api/swap-applications/me` | SwapRequest | 내 지원 내역(직원) | 200 |
| 31 | GET | `/api/notices` | Notice | 공지 피드 | 200 |
| 32 | POST | `/api/notices` | Notice | 공지 작성(사장) | 201 |
| 33 | GET | `/api/notices/{noticeId}` | Notice | 공지 상세 | 200 |
| 34 | DELETE | `/api/notices/{noticeId}` | Notice | 공지 삭제(사장) | 204 |
| 35 | POST | `/api/notices/attachments` | Notice | 첨부 업로드 | 201 |
| 36 | GET | `/api/notices/unread` | Notice | 미확인 공지(레드 닷) | 200 |
| 37 | POST | `/api/notices/read` | Notice | 공지 읽음 처리 | 204 |
| 38 | GET | `/api/notifications` | Notification | 알림 인박스 | 200 |
| 39 | GET | `/api/notifications/unread-count` | Notification | 안 읽은 알림 수 | 200 |
| 40 | PATCH | `/api/notifications/{notificationId}/read` | Notification | 알림 읽음 | 204 |
| 41 | POST | `/api/notifications/read-all` | Notification | 전체 읽음 | 204 |
| 42 | GET | `/api/payroll` | Payroll | 인건비 집계(사장) | 200 |
| 43 | GET | `/api/health` | Health | 헬스 체크 🔓 | 200 |

> 🔓 = 인증 불필요(공개). 그 외 모든 엔드포인트는 `Authorization: Bearer {token}` 필요.
>
> **참고:** 본 문서는 PTManager의 *목표(target) API 명세*다. 현재 백엔드 코드는 MVP 초기 단계로 일부 엔드포인트(JWT 인증/리프레시, 회원가입, 대타 지원·승인 분리, 페이지네이션, FCM 토큰, 공지 첨부/읽음, 알림 배지, Payroll 등)를 아직 구현하지 않았다. 데이터 모델 상세는 [ERD.md](ERD.md) 참고.
