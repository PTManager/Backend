package com.ptmanager.backend.config

import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.domain.HandoverCategory
import com.ptmanager.backend.domain.HandoverNote
import com.ptmanager.backend.domain.JoinRequest
import com.ptmanager.backend.domain.JoinRequestStatus
import com.ptmanager.backend.domain.Notice
import com.ptmanager.backend.domain.Notification
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.domain.Shift
import com.ptmanager.backend.domain.SwapApplication
import com.ptmanager.backend.domain.SwapRequest
import com.ptmanager.backend.domain.SwapRequestStatus
import com.ptmanager.backend.domain.User
import com.ptmanager.backend.domain.UserRole
import com.ptmanager.backend.domain.Workplace
import com.ptmanager.backend.repository.HandoverNoteRepository
import com.ptmanager.backend.repository.JoinRequestRepository
import com.ptmanager.backend.repository.NoticeRepository
import com.ptmanager.backend.repository.NotificationRepository
import com.ptmanager.backend.repository.ShiftRepository
import com.ptmanager.backend.repository.SwapApplicationRepository
import com.ptmanager.backend.repository.SwapRequestRepository
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.repository.WorkplaceRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 운영(prod) 프로파일에서는 시드 데이터를 넣지 않는다. (local/test/시연 전용)
// 배포 서버(EC2, prod)에서 시연 데이터를 보려면 이 서버를 prod 가 아닌 프로파일로
// 실행하거나 아래 @Profile 을 잠시 완화한다.
@Profile("!prod")
@Component
class DataSeeder(
    private val workplaceRepository: WorkplaceRepository,
    private val userRepository: UserRepository,
    private val shiftRepository: ShiftRepository,
    private val noticeRepository: NoticeRepository,
    private val handoverNoteRepository: HandoverNoteRepository,
    private val swapRequestRepository: SwapRequestRepository,
    private val swapApplicationRepository: SwapApplicationRepository,
    private val joinRequestRepository: JoinRequestRepository,
    private val notificationRepository: NotificationRepository,
    private val passwordEncoder: PasswordEncoder,
) : CommandLineRunner {

    private val zone = ZoneId.of("Asia/Seoul")

    override fun run(vararg args: String?) {
        if (userRepository.count() > 0) return

        val workplace = workplaceRepository.save(
            Workplace(name = "PT Manager Cafe", address = "Seoul Gangnam-gu", inviteCode = "CAFE01"),
        )
        val wid = workplace.id!!

        // 사장 1 + 직원 3 (전부 비밀번호 "password"). 시급을 다르게 둬 인건비 화면이 구분되게.
        val employer = saveUser(wid, "employer@ptmanager.test", "Park Employer", UserRole.EMPLOYER, 0)
        val kim = saveUser(wid, "employee@ptmanager.test", "Kim Employee", UserRole.EMPLOYEE, 12000)
        val lee = saveUser(wid, "employee2@ptmanager.test", "Lee Alba", UserRole.EMPLOYEE, 11000)
        val choi = saveUser(wid, "employee3@ptmanager.test", "Choi Alba", UserRole.EMPLOYEE, 13000)
        // 매장 미소속 지원자 — 사장 화면의 '참여 요청'을 채우기 위한 계정.
        val applicant = saveUser(null, "applicant@ptmanager.test", "Jung Newbie", UserRole.EMPLOYEE, 10030)

        seedShifts(wid, listOf(kim.id!!, lee.id!!, choi.id!!))
        seedNotices(wid, employer.id!!)
        seedHandovers(wid, employer.id!!, kim.id!!, lee.id!!)
        val (pendingSwapId, swapShiftId) = seedSwaps(wid, kim.id!!, lee.id!!, choi.id!!)
        val joinReqId = joinRequestRepository.save(
            JoinRequest(workplaceId = wid, userId = applicant.id!!, status = JoinRequestStatus.PENDING),
        ).id!!

        seedNotifications(employer.id!!, kim.id!!, joinReqId, pendingSwapId, swapShiftId)
    }

    private fun saveUser(workplaceId: Long?, email: String, name: String, role: UserRole, wage: Int) =
        userRepository.save(
            User(
                email = email,
                password = passwordEncoder.encode("password"),
                name = name,
                role = role,
                workplaceId = workplaceId,
                hourlyWage = wage,
            ),
        )

    /**
     * 각 직원에게 지난 14일 ~ 앞으로 7일 스케줄을 만든다.
     * 지난 근무는 출퇴근 기록(PRESENT)까지 넣어 '누적 인건비'가, 미래 근무는 SCHEDULED 로
     * '근무 스케줄'이 채워지게 한다. 하루 하나는 지각(LATE), 하나는 결근(ABSENT)로 다양화.
     */
    private fun seedShifts(workplaceId: Long, employeeIds: List<Long>) {
        val today = LocalDate.now()
        val start = LocalTime.of(9, 0)
        val end = LocalTime.of(14, 0)

        // ponytail: shift id 1·2 는 첫 직원(kim)의 SCHEDULED 근무로 고정 — BaselineApiTests 가
        // 하드코딩한 계약. 데모 로테이션보다 반드시 먼저 저장해야 id 가 1,2 로 매겨진다.
        for (dayOffset in 0..1) {
            shiftRepository.save(
                Shift(
                    workplaceId = workplaceId,
                    employeeId = employeeIds[0],
                    workDate = today.plusDays(dayOffset.toLong()),
                    startTime = if (dayOffset == 0) start else LocalTime.of(14, 0),
                    endTime = if (dayOffset == 0) end else LocalTime.of(20, 0),
                    attendanceStatus = AttendanceStatus.SCHEDULED,
                ),
            )
        }

        for (offset in -14..7) {
            val date = today.plusDays(offset.toLong())
            val employeeId = employeeIds[(offset + 14) % employeeIds.size]
            val past = offset < 0
            val late = past && offset % 5 == 0
            val absent = past && offset % 7 == 0
            val status = when {
                absent -> AttendanceStatus.ABSENT
                late -> AttendanceStatus.LATE
                past -> AttendanceStatus.PRESENT
                else -> AttendanceStatus.SCHEDULED
            }
            val checkedIn = if (past && !absent) {
                val inTime = if (late) start.plusMinutes(18) else start.plusMinutes(2)
                date.atTime(inTime).atZone(zone).toInstant()
            } else null
            val checkedOut = if (past && !absent) date.atTime(end).atZone(zone).toInstant() else null

            shiftRepository.save(
                Shift(
                    workplaceId = workplaceId,
                    employeeId = employeeId,
                    workDate = date,
                    startTime = start,
                    endTime = end,
                    checkedInAt = checkedIn,
                    checkedOutAt = checkedOut,
                    attendanceStatus = status,
                ),
            )
        }
    }

    private fun seedNotices(workplaceId: Long, employerId: Long) {
        listOf(
            "여름 성수기 근무 안내" to "7월 한 달간 주말 오픈을 1시간 앞당깁니다. 스케줄 확인 부탁드려요.",
            "신메뉴 교육" to "이번 주 금요일 마감 후 신메뉴 제조 교육이 있습니다. 참석 필수입니다.",
            "급여 지급일 변경" to "이번 달부터 급여 지급일이 매월 10일로 변경됩니다.",
        ).forEach { (title, body) ->
            noticeRepository.save(
                Notice(workplaceId = workplaceId, authorId = employerId, title = title, body = body),
            )
        }
    }

    private data class HandoverSeed(
        val authorId: Long,
        val category: HandoverCategory,
        val title: String,
        val content: String,
    )

    private fun seedHandovers(workplaceId: Long, employerId: Long, kimId: Long, leeId: Long) {
        listOf(
            HandoverSeed(kimId, HandoverCategory.STOCK, "우유 발주 필요", "우유 2팩 남았습니다. 내일 발주 넣어주세요."),
            HandoverSeed(leeId, HandoverCategory.DEVICE, "2번 머신 압력 불안정", "2번 에스프레소 머신 추출 압력이 불안정합니다. AS 요청 필요."),
            HandoverSeed(kimId, HandoverCategory.CUSTOMER, "텀블러 분실물 보관", "단골 손님이 텀블러 두고 가셨어요. 카운터 서랍에 보관 중입니다."),
            HandoverSeed(employerId, HandoverCategory.STOCK, "원두 샘플 시음", "원두 신규 거래처 샘플 도착. 마감 후 시음 부탁."),
        ).forEach { (authorId, category, title, content) ->
            handoverNoteRepository.save(
                HandoverNote(
                    workplaceId = workplaceId,
                    authorId = authorId,
                    category = category,
                    title = title,
                    content = content,
                ),
            )
        }
    }

    /**
     * 대타 시나리오 두 가지:
     *  1) PENDING 요청 + 지원자 2명(SwapApplication) → 사장 화면의 '대타 승인 요청'을 채움
     *  2) 이미 APPROVED 되어 대타(substitute)가 확정된 요청 → 처리 완료 이력
     * @return (승인 대기 중인 SwapRequest id, 그 요청 대상 shift id)
     */
    private fun seedSwaps(workplaceId: Long, kimId: Long, leeId: Long, choiId: Long): Pair<Long, Long> {
        val today = LocalDate.now()

        // Kim 의 모레 근무를 대타 요청 (승인 대기)
        val targetShift = shiftRepository.save(
            Shift(
                workplaceId = workplaceId,
                employeeId = kimId,
                workDate = today.plusDays(2),
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(20, 0),
                attendanceStatus = AttendanceStatus.SCHEDULED,
            ),
        )
        val pending = swapRequestRepository.save(
            SwapRequest(
                workplaceId = workplaceId,
                shiftId = targetShift.id!!,
                requesterId = kimId,
                reason = "개인 사정으로 이날 근무가 어렵습니다. 대타 구합니다.",
                status = SwapRequestStatus.PENDING,
            ),
        )
        listOf(leeId, choiId).forEach { applicantId ->
            swapApplicationRepository.save(
                SwapApplication(
                    swapRequestId = pending.id!!,
                    applicantId = applicantId,
                    status = SwapRequestStatus.PENDING,
                ),
            )
        }

        // 이미 승인되어 Choi 가 대타로 확정된 지난 요청
        val approvedShift = shiftRepository.save(
            Shift(
                workplaceId = workplaceId,
                employeeId = choiId,
                workDate = today.minusDays(3),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(14, 0),
                attendanceStatus = AttendanceStatus.PRESENT,
                checkedInAt = today.minusDays(3).atTime(9, 1).atZone(zone).toInstant(),
                checkedOutAt = today.minusDays(3).atTime(14, 0).atZone(zone).toInstant(),
            ),
        )
        swapRequestRepository.save(
            SwapRequest(
                workplaceId = workplaceId,
                shiftId = approvedShift.id!!,
                requesterId = leeId,
                substituteId = choiId,
                reason = "병원 예약이 겹쳐 대타 부탁드렸습니다.",
                status = SwapRequestStatus.APPROVED,
            ),
        )

        return pending.id!! to targetShift.id!!
    }

    private fun seedNotifications(
        employerId: Long,
        kimId: Long,
        joinRequestId: Long,
        swapRequestId: Long,
        swapShiftId: Long,
    ) {
        // 사장: 승인 대기 알림들 (읽지 않음)
        notificationRepository.save(
            Notification(
                userId = employerId,
                type = NotificationType.JOIN_REQUEST,
                message = "Jung Newbie 님이 매장 참여를 요청했습니다.",
                targetType = "JOIN_REQUEST",
                targetId = joinRequestId,
            ),
        )
        notificationRepository.save(
            Notification(
                userId = employerId,
                type = NotificationType.SWAP_APPLICATION,
                message = "대타 요청에 지원 2건이 접수되었습니다.",
                targetType = "SWAP_REQUEST",
                targetId = swapRequestId,
            ),
        )
        // 직원: 결과/공지 알림 (하나는 읽음 처리)
        notificationRepository.save(
            Notification(
                userId = kimId,
                type = NotificationType.NOTICE,
                message = "새 공지 '여름 성수기 근무 안내'가 등록되었습니다.",
                targetType = "NOTICE",
                targetId = null,
                read = true,
            ),
        )
        notificationRepository.save(
            Notification(
                userId = kimId,
                type = NotificationType.SWAP_REQUEST,
                message = "모레 근무 대타 요청이 접수 대기 중입니다.",
                targetType = "SHIFT",
                targetId = swapShiftId,
            ),
        )
    }
}
