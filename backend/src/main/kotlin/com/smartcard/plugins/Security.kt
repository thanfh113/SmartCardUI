package com.smartcard.plugins

import com.smartcard.models.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureSecurity() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        anyHost() // For development - restrict in production
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = cause.message ?: "Bad request",
                    code = "BAD_REQUEST"
                )
            )
        }
        
        exception<SecurityException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(
                    error = cause.message ?: "Security error",
                    code = "SECURITY_ERROR"
                )
            )
        }
        
        exception<NoSuchElementException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = cause.message ?: "Resource not found",
                    code = "NOT_FOUND"
                )
            )
        }
        
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = cause.message ?: "Internal server error",
                    code = "INTERNAL_ERROR"
                )
            )
        }
    }
}
