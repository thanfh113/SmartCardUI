package org.example.project.model

import java.time.LocalDateTime

enum class TransactionType { TOP_UP, PAYMENT }

data class Transaction(
    val time: LocalDateTime,
    val type: TransactionType,
    val amount: Double,
    val description: String,
    val balanceAfter: Double
)
