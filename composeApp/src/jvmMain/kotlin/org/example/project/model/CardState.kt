package org.example.project.model

data class CardState(
    val maxPinTries: Int = 3,
    val pinTriesRemaining: Int = 3,
    val isBlocked: Boolean = false,
    val balance: Double = 0.0
)
