package com.ptmanager.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PtManagerBackendApplication

fun main(args: Array<String>) {
    runApplication<PtManagerBackendApplication>(*args)
}
