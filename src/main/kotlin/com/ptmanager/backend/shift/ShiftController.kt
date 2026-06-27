package com.ptmanager.backend.shift

import com.ptmanager.backend.domain.Shift
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shifts")
class ShiftController(
    private val shiftService: ShiftService,
) {

    @GetMapping
    fun findShifts(@RequestParam userId: Long): List<Shift> = shiftService.findShiftsByUser(userId)

    @PostMapping("/{id}/check-in")
    fun checkIn(@PathVariable id: Long): Shift = shiftService.checkIn(id)
}
