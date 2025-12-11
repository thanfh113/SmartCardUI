package com.smartcard.routes

import com.smartcard.models.*
import com.smartcard.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.departmentRoutes(departmentService: DepartmentService) {
    route("/api/departments") {
        get {
            val departments = departmentService.getAll()
            call.respond(ApiResponse(success = true, data = departments))
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val department = departmentService.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Department not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = department))
        }

        post {
            val dto = call.receive<DepartmentDTO>()
            val department = departmentService.create(dto)
            call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = department))
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val dto = call.receive<DepartmentDTO>()
            val department = departmentService.update(id, dto)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Department not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = department))
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val deleted = departmentService.delete(id)
            if (deleted) {
                call.respond(ApiResponse(success = true, data = "Department deleted"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Department not found", code = "NOT_FOUND"))
            }
        }
    }
}

fun Route.positionRoutes(positionService: PositionService) {
    route("/api/positions") {
        get {
            val positions = positionService.getAll()
            call.respond(ApiResponse(success = true, data = positions))
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val position = positionService.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Position not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = position))
        }

        post {
            val dto = call.receive<PositionDTO>()
            val position = positionService.create(dto)
            call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = position))
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val dto = call.receive<PositionDTO>()
            val position = positionService.update(id, dto)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Position not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = position))
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val deleted = positionService.delete(id)
            if (deleted) {
                call.respond(ApiResponse(success = true, data = "Position deleted"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Position not found", code = "NOT_FOUND"))
            }
        }
    }
}

fun Route.productRoutes(productService: ProductService) {
    route("/api/products") {
        get {
            val availableOnly = call.request.queryParameters["available"]?.toBoolean() ?: false
            val products = if (availableOnly) {
                productService.getAvailable()
            } else {
                productService.getAll()
            }
            call.respond(ApiResponse(success = true, data = products))
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val product = productService.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Product not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = product))
        }

        post {
            val dto = call.receive<ProductDTO>()
            val product = productService.create(dto)
            call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = product))
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val dto = call.receive<ProductDTO>()
            val product = productService.update(id, dto)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Product not found", code = "NOT_FOUND"))
            
            call.respond(ApiResponse(success = true, data = product))
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid ID", code = "INVALID_ID"))
            
            val deleted = productService.delete(id)
            if (deleted) {
                call.respond(ApiResponse(success = true, data = "Product deleted"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Product not found", code = "NOT_FOUND"))
            }
        }
    }
}
