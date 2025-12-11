package com.smartcard.routes

import com.smartcard.models.*
import com.smartcard.services.EmployeeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.employeeRoutes(employeeService: EmployeeService) {
    route("/api/employees") {
        
        // Get all employees
        get {
            val employees = employeeService.getAllEmployees()
            call.respond(ApiResponse(success = true, data = employees))
        }

        // Get employee by ID
        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val employee = employeeService.getEmployeeById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Employee not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = employee))
        }

        // Get employee by card UUID
        get("/card/{uuid}") {
            val uuid = call.parameters["uuid"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "UUID required", code = "INVALID_UUID"))
            
            val employee = employeeService.getEmployeeByCardUuid(uuid)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Employee not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = employee))
        }

        // Create new employee
        post {
            val request = call.receive<CreateEmployeeRequest>()
            val employee = employeeService.createEmployee(request)
            call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = employee))
        }

        // Update employee
        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val request = call.receive<UpdateEmployeeRequest>()
            val employee = employeeService.updateEmployee(id, request)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Employee not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = employee))
        }

        // Delete employee
        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val deleted = employeeService.deleteEmployee(id)
            if (deleted) {
                call.respond(ApiResponse(success = true, data = "Employee deleted"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Employee not found", code = "NOT_FOUND"))
            }
        }

        // Sync card data
        post("/{id}/sync") {
            val request = call.receive<CardSyncRequest>()
            val employee = employeeService.syncCardData(request)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Employee not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = employee))
        }

        // Get employee balance
        get("/{id}/balance") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val employee = employeeService.getEmployeeById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Employee not found", code = "NOT_FOUND"))
            
            val balance = BalanceResponse(
                employeeId = employee.id!!,
                employeeName = employee.name,
                balance = employee.balance
            )
            call.respond(ApiResponse(success = true, data = balance))
        }
    }
}
