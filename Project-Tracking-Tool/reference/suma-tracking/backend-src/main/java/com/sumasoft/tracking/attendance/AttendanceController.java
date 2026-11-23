package com.sumasoft.tracking.attendance;

import com.sumasoft.tracking.attendance.AttendanceDtos.CloseForm;
import com.sumasoft.tracking.attendance.AttendanceDtos.Day;
import com.sumasoft.tracking.attendance.AttendanceDtos.DeviceInfo;
import com.sumasoft.tracking.attendance.AttendanceDtos.Event;
import com.sumasoft.tracking.attendance.AttendanceDtos.MyDay;
import com.sumasoft.tracking.attendance.AttendanceDtos.ScheduleForm;
import com.sumasoft.tracking.attendance.AttendanceDtos.ScheduleRow;
import com.sumasoft.tracking.attendance.AttendanceDtos.Team;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Attendance (Step 41).
 * Every employee:
 *   GET  /api/attendance/me                 today: timer, break, flags
 *   POST /api/attendance/me/punch-in        {deviceId, deviceLabel}
 *   POST /api/attendance/me/break-start
 *   POST /api/attendance/me/break-end
 *   POST /api/attendance/me/punch-out
 *   POST /api/attendance/me/seen            once after sign-in (saves IP and device)
 *   GET  /api/attendance/me/history?days=31
 * ATTENDANCE_VIEW or ATTENDANCE_MANAGE (Super Admin always):
 *   GET  /api/attendance/team?date=2026-11-20
 *   GET  /api/attendance/people/{userId}/events?days=30
 *   GET  /api/attendance/people/{userId}/days?days=31
 *   GET  /api/attendance/schedules
 * ATTENDANCE_MANAGE:
 *   PUT  /api/attendance/schedules/{userId} {shiftMinutes, breakMinutes}
 *   POST /api/attendance/days/{dayId}/close {punchOutTime: "18:30", note}
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public MyDay me(HttpServletRequest request) {
        return service.me(request);
    }

    @PostMapping("/me/punch-in")
    public MyDay punchIn(@Valid @RequestBody(required = false) DeviceInfo device, HttpServletRequest request) {
        return service.punchIn(device, request);
    }

    @PostMapping("/me/break-start")
    public MyDay breakStart(@Valid @RequestBody(required = false) DeviceInfo device, HttpServletRequest request) {
        return service.breakStart(device, request);
    }

    @PostMapping("/me/break-end")
    public MyDay breakEnd(@Valid @RequestBody(required = false) DeviceInfo device, HttpServletRequest request) {
        return service.breakEnd(device, request);
    }

    @PostMapping("/me/punch-out")
    public MyDay punchOut(@Valid @RequestBody(required = false) DeviceInfo device, HttpServletRequest request) {
        return service.punchOut(device, request);
    }

    @PostMapping("/me/seen")
    public List<String> seen(@Valid @RequestBody(required = false) DeviceInfo device, HttpServletRequest request) {
        return service.seen(device, request);
    }

    @GetMapping("/me/history")
    public List<Day> myHistory(@RequestParam(defaultValue = "31") int days) {
        return service.myHistory(days);
    }

    @GetMapping("/team")
    public Team team(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.team(date);
    }

    @GetMapping("/people/{userId}/events")
    public List<Event> events(@PathVariable long userId, @RequestParam(defaultValue = "30") int days) {
        return service.events(userId, days);
    }

    @GetMapping("/people/{userId}/days")
    public List<Day> personDays(@PathVariable long userId, @RequestParam(defaultValue = "31") int days) {
        return service.personHistory(userId, days);
    }

    @GetMapping("/schedules")
    public List<ScheduleRow> schedules() {
        return service.schedules();
    }

    @PutMapping("/schedules/{userId}")
    public ScheduleRow saveSchedule(@PathVariable long userId, @Valid @RequestBody ScheduleForm form) {
        return service.saveSchedule(userId, form);
    }

    @PostMapping("/days/{dayId}/close")
    public Day close(@PathVariable long dayId, @Valid @RequestBody CloseForm form, HttpServletRequest request) {
        return service.closeDay(dayId, form, request);
    }
}
