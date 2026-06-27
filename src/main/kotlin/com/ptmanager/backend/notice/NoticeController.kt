package com.ptmanager.backend.notice

import com.ptmanager.backend.domain.Notice
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notices")
class NoticeController(
    private val noticeService: NoticeService,
) {

    @GetMapping
    fun findNotices(@RequestParam workplaceId: Long): List<Notice> =
        noticeService.findByWorkplace(workplaceId)

    @GetMapping("/{id}")
    fun findNotice(@PathVariable id: Long): Notice = noticeService.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNotice(@Valid @RequestBody request: CreateNoticeRequest): Notice =
        noticeService.create(request.workplaceId, request.authorId, request.title, request.body)

    data class CreateNoticeRequest(
        val workplaceId: Long,
        val authorId: Long,
        @field:NotBlank val title: String,
        @field:NotBlank val body: String,
    )
}
