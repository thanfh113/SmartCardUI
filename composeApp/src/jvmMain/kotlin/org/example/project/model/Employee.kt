package org.example.project.model

data class Employee(
    val id: String,
    val name: String,
    val dob: String,
    val department: String,
    val position: String,
    val role: String,
    val photoPath: String? = null,
    val isDefaultPin: Boolean
)
