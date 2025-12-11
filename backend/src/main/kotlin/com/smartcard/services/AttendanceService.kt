package com.smartcard.services

import com.smartcard.data.DatabaseFactory.dbQuery
import com.smartcard.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalTime

class AttendanceService {

    private val businessStartTime = LocalTime.of(8, 0) // 08:00
    private val lateThreshold = LocalTime.of(8, 30)    // 08:30

    suspend fun checkIn(request: CheckInRequest): AttendanceLogDTO = dbQuery {
        // Check if already checked in for this date
        val existing = AttendanceLogs
            .select { 
                (AttendanceLogs.employeeId eq request.employeeId) and 
                (AttendanceLogs.workDate eq request.workDate) 
            }
            .singleOrNull()

        if (existing != null) {
            throw IllegalArgumentException("Already checked in for this date")
        }

        val checkInTime = LocalTime.parse(request.checkInTime)
        val status = when {
            checkInTime <= businessStartTime -> "ON_TIME"
            checkInTime <= lateThreshold -> "LATE"
            else -> "LATE"
        }

        val id = AttendanceLogs.insertAndGetId {
            it[employeeId] = request.employeeId
            it[workDate] = request.workDate
            it[checkInTime] = request.checkInTime
            it[AttendanceLogs.status] = status
        }

        getAttendanceById(id.value)!!
    }

    suspend fun checkOut(request: CheckOutRequest): AttendanceLogDTO = dbQuery {
        val attendance = AttendanceLogs
            .select { 
                (AttendanceLogs.employeeId eq request.employeeId) and 
                (AttendanceLogs.workDate eq request.workDate) 
            }
            .singleOrNull() ?: throw IllegalArgumentException("No check-in record found for this date")

        if (attendance[AttendanceLogs.checkOutTime] != null) {
            throw IllegalArgumentException("Already checked out for this date")
        }

        AttendanceLogs.update({ 
            (AttendanceLogs.employeeId eq request.employeeId) and 
            (AttendanceLogs.workDate eq request.workDate) 
        }) {
            it[checkOutTime] = request.checkOutTime
        }

        getAttendanceByEmployeeAndDate(request.employeeId, request.workDate)!!
    }

    suspend fun getAttendanceHistory(employeeId: Int, limit: Int = 30): List<AttendanceLogDTO> = dbQuery {
        AttendanceLogs
            .innerJoin(Employees)
            .select { AttendanceLogs.employeeId eq employeeId }
            .orderBy(AttendanceLogs.workDate to SortOrder.DESC)
            .limit(limit)
            .map { toAttendanceDTO(it) }
    }

    suspend fun getAttendanceReport(request: AttendanceReportRequest): List<AttendanceLogDTO> = dbQuery {
        var query = AttendanceLogs.innerJoin(Employees).selectAll()

        request.employeeId?.let { empId ->
            query = query.andWhere { AttendanceLogs.employeeId eq empId }
        }

        request.startDate?.let { start ->
            query = query.andWhere { AttendanceLogs.workDate greaterEq start }
        }

        request.endDate?.let { end ->
            query = query.andWhere { AttendanceLogs.workDate lessEq end }
        }

        query.orderBy(AttendanceLogs.workDate to SortOrder.DESC)
            .map { toAttendanceDTO(it) }
    }

    private suspend fun getAttendanceById(id: Int): AttendanceLogDTO? = dbQuery {
        AttendanceLogs
            .innerJoin(Employees)
            .select { AttendanceLogs.id eq id }
            .map { toAttendanceDTO(it) }
            .singleOrNull()
    }

    private suspend fun getAttendanceByEmployeeAndDate(employeeId: Int, workDate: String): AttendanceLogDTO? = dbQuery {
        AttendanceLogs
            .innerJoin(Employees)
            .select { 
                (AttendanceLogs.employeeId eq employeeId) and 
                (AttendanceLogs.workDate eq workDate) 
            }
            .map { toAttendanceDTO(it) }
            .singleOrNull()
    }

    private fun toAttendanceDTO(row: ResultRow): AttendanceLogDTO {
        return AttendanceLogDTO(
            id = row[AttendanceLogs.id].value,
            employeeId = row[AttendanceLogs.employeeId].value,
            employeeName = row[Employees.name],
            workDate = row[AttendanceLogs.workDate],
            checkInTime = row[AttendanceLogs.checkInTime],
            checkOutTime = row[AttendanceLogs.checkOutTime],
            status = row[AttendanceLogs.status],
            notes = row[AttendanceLogs.notes]
        )
    }
}
