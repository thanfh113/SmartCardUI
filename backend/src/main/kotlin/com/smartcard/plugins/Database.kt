package com.smartcard.plugins

import com.smartcard.data.DatabaseFactory
import io.ktor.server.application.*

fun Application.configureDatabase() {
    DatabaseFactory.init(environment.config)
}
