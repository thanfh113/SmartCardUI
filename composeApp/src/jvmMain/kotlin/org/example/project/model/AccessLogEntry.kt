package org.example.project.model

import java.time.LocalDateTime

enum class AccessType { CHECK_IN, CHECK_OUT, RESTRICTED_AREA }

data class AccessLogEntry(
    val time: LocalDateTime,
    val accessType: AccessType,
    val description: String
)
