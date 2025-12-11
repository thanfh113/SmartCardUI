package com.smartcard.routes

import com.smartcard.models.*
import com.smartcard.services.AttendanceService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.attendanceRoutes(attendanceService: AttendanceService) {
    route("/api/attendance") {
        
        // Check in
        post("/checkin") {
            val request = call.receive<CheckInRequest>()
            val attendance = attendanceService.checkIn(request)
            call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = attendance))
        }

        // Check out
        post("/checkout") {
            val request = call.receive<CheckOutRequest>()
            val attendance = attendanceService.checkOut(request)
            call.respond(ApiResponse(success = true, data = attendance))
        }

        // Get attendance history for employee
        get("/{emp_id}") {
            val empId = call.parameters["emp_id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid employee ID", code = "INVALID_ID"))
            
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 30
            val history = attendanceService.getAttendanceHistory(empId, limit)
            call.respond(ApiResponse(success = true, data = history))
        }

        // Get attendance report (filtered)
        get("/report") {
            val startDate = call.request.queryParameters["startDate"]
            val endDate = call.request.queryParameters["endDate"]
            val empId = call.request.queryParameters["employeeId"]?.toIntOrNull()
            
            val request = AttendanceReportRequest(
                startDate = startDate,
                endDate = endDate,
                employeeId = empId
            )
            
            val report = attendanceService.getAttendanceReport(request)
            call.respond(ApiResponse(success = true, data = report))
        }
    }
}
