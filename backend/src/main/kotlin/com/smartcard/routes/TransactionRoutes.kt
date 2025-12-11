package com.smartcard.routes

import com.smartcard.models.*
import com.smartcard.services.TransactionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.transactionRoutes(transactionService: TransactionService) {
    route("/api/transactions") {
        
        // Top-up
        post("/topup") {
            val request = call.receive<TopUpRequest>()
            val transaction = transactionService.topUp(request)
            call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = transaction))
        }

        // Payment
        post("/payment") {
            val request = call.receive<PaymentRequest>()
            val transaction = transactionService.processPayment(request)
            call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = transaction))
        }

        // Get transaction history
        get("/{emp_id}") {
            val empId = call.parameters["emp_id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid employee ID", code = "INVALID_ID"))
            
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val transactions = transactionService.getTransactionHistory(empId, limit)
            call.respond(ApiResponse(success = true, data = transactions))
        }
    }
}
