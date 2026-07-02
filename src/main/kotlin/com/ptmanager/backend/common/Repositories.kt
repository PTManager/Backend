package com.ptmanager.backend.common

import java.util.Optional

/** 값이 없으면 404로 매핑되는 NoSuchElementException을 던진다. (findById(...).orNotFound("X not found.")) */
fun <T> Optional<T>.orNotFound(message: String): T =
    orElseThrow { NoSuchElementException(message) }
