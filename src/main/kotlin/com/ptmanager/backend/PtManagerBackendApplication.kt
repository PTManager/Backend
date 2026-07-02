package com.ptmanager.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PtManagerBackendApplication

fun main(args: Array<String>) {
    runApplication<PtManagerBackendApplication>(*args)
}
