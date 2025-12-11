package com.smartcard.plugins

import com.smartcard.routes.*
import com.smartcard.services.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    // Initialize services
    val cryptoService = CryptoService()
    val employeeService = EmployeeService(cryptoService)
    val transactionService = TransactionService(employeeService, cryptoService)
    val attendanceService = AttendanceService()
    val departmentService = DepartmentService()
    val positionService = PositionService()
    val productService = ProductService()

    routing {
        get("/") {
            call.respondText("SmartCard Backend API v1.0.0")
        }

        get("/health") {
            call.respondText("OK")
        }

        // Register all routes
        employeeRoutes(employeeService)
        transactionRoutes(transactionService)
        attendanceRoutes(attendanceService)
        departmentRoutes(departmentService)
        positionRoutes(positionService)
        productRoutes(productService)
    }
}
