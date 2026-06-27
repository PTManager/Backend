package com.ptmanager.backend.common.dto

/** 페이지네이션 공통 응답 래퍼. (Notice/Notification 목록 등) */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
