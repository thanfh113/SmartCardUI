package com.smartcard.services

import com.smartcard.data.DatabaseFactory.dbQuery
import com.smartcard.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

class EmployeeService(private val cryptoService: CryptoService) {

    suspend fun getAllEmployees(): List<EmployeeDTO> = dbQuery {
        Employees
            .innerJoin(Departments)
            .innerJoin(Positions)
            .selectAll()
            .map { toEmployeeDTO(it) }
    }

    suspend fun getEmployeeById(id: Int): EmployeeDTO? = dbQuery {
        Employees
            .innerJoin(Departments)
            .innerJoin(Positions)
            .select { Employees.id eq id }
            .map { toEmployeeDTO(it) }
            .singleOrNull()
    }

    suspend fun getEmployeeByCardUuid(cardUuid: String): EmployeeDTO? = dbQuery {
        Employees
            .innerJoin(Departments)
            .innerJoin(Positions)
            .select { Employees.cardUuid eq cardUuid }
            .map { toEmployeeDTO(it) }
            .singleOrNull()
    }

    suspend fun createEmployee(request: CreateEmployeeRequest): EmployeeDTO = dbQuery {
        // Check if card UUID or employee ID already exists
        val existingByCard = Employees.select { Employees.cardUuid eq request.cardUuid }.count() > 0
        val existingByEmpId = Employees.select { Employees.employeeId eq request.employeeId }.count() > 0
        
        if (existingByCard) {
            throw IllegalArgumentException("Card UUID already registered")
        }
        if (existingByEmpId) {
            throw IllegalArgumentException("Employee ID already exists")
        }

        val publicKeyBytes = request.rsaPublicKey?.let { cryptoService.decodeBase64(it) }

        val id = Employees.insertAndGetId {
            it[cardUuid] = request.cardUuid
            it[employeeId] = request.employeeId
            it[name] = request.name
            it[dateOfBirth] = request.dateOfBirth
            it[departmentId] = request.departmentId
            it[positionId] = request.positionId
            it[role] = EmployeeRole.valueOf(request.role)
            it[balance] = BigDecimal.ZERO
            it[photoPath] = request.photoPath
            it[rsaPublicKey] = publicKeyBytes
            it[pinHash] = request.pinHash
            it[isActive] = true
        }

        getEmployeeById(id.value)!!
    }

    suspend fun updateEmployee(id: Int, request: UpdateEmployeeRequest): EmployeeDTO? = dbQuery {
        val exists = Employees.select { Employees.id eq id }.count() > 0
        if (!exists) return@dbQuery null

        Employees.update({ Employees.id eq id }) {
            request.name?.let { name -> it[Employees.name] = name }
            request.dateOfBirth?.let { dob -> it[dateOfBirth] = dob }
            request.departmentId?.let { deptId -> it[departmentId] = deptId }
            request.positionId?.let { posId -> it[positionId] = posId }
            request.photoPath?.let { path -> it[photoPath] = path }
            request.isActive?.let { active -> it[isActive] = active }
        }

        getEmployeeById(id)
    }

    suspend fun deleteEmployee(id: Int): Boolean = dbQuery {
        Employees.deleteWhere { Employees.id eq id } > 0
    }

    suspend fun syncCardData(request: CardSyncRequest): EmployeeDTO? = dbQuery {
        val employee = Employees.select { Employees.cardUuid eq request.cardUuid }
            .singleOrNull() ?: return@dbQuery null

        Employees.update({ Employees.cardUuid eq request.cardUuid }) {
            request.rsaPublicKey?.let { key ->
                it[rsaPublicKey] = cryptoService.decodeBase64(key)
            }
            request.balance?.let { bal ->
                it[balance] = bal
            }
        }

        getEmployeeByCardUuid(request.cardUuid)
    }

    suspend fun updateBalance(employeeId: Int, newBalance: BigDecimal): Boolean = dbQuery {
        Employees.update({ Employees.id eq employeeId }) {
            it[balance] = newBalance
        } > 0
    }

    suspend fun getBalance(employeeId: Int): BigDecimal? = dbQuery {
        Employees.select { Employees.id eq employeeId }
            .map { it[Employees.balance] }
            .singleOrNull()
    }

    private fun toEmployeeDTO(row: ResultRow): EmployeeDTO {
        return EmployeeDTO(
            id = row[Employees.id].value,
            cardUuid = row[Employees.cardUuid],
            employeeId = row[Employees.employeeId],
            name = row[Employees.name],
            dateOfBirth = row[Employees.dateOfBirth],
            departmentId = row[Employees.departmentId].value,
            departmentName = row[Departments.name],
            positionId = row[Employees.positionId].value,
            positionName = row[Positions.name],
            role = row[Employees.role].name,
            balance = row[Employees.balance],
            photoPath = row[Employees.photoPath],
            isActive = row[Employees.isActive]
        )
    }
}
