package com.smartcard.data

import com.smartcard.models.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(config: ApplicationConfig) {
        val dbConfig = config.config("database")
        val database = Database.connect(createHikariDataSource(dbConfig))
        
        transaction(database) {
            // Create tables if they don't exist
            SchemaUtils.create(
                Departments,
                Positions,
                Employees,
                Products,
                Transactions,
                AttendanceLogs
            )
        }
    }

    private fun createHikariDataSource(config: ApplicationConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            driverClassName = config.property("driver").getString()
            jdbcUrl = config.property("url").getString()
            username = config.property("user").getString()
            password = config.property("password").getString()
            maximumPoolSize = config.propertyOrNull("maxPoolSize")?.getString()?.toInt() ?: 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(hikariConfig)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
