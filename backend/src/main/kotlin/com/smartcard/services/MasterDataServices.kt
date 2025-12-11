package com.smartcard.services

import com.smartcard.data.DatabaseFactory.dbQuery
import com.smartcard.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class DepartmentService {

    suspend fun getAll(): List<DepartmentDTO> = dbQuery {
        Departments.selectAll().map { toDepartmentDTO(it) }
    }

    suspend fun getById(id: Int): DepartmentDTO? = dbQuery {
        Departments.selectAll().where { Departments.id eq id }
            .map { toDepartmentDTO(it) }
            .singleOrNull()
    }

    suspend fun create(dto: DepartmentDTO): DepartmentDTO = dbQuery {
        val id = Departments.insertAndGetId {
            it[name] = dto.name
            it[description] = dto.description
        }
        getById(id.value)!!
    }

    suspend fun update(id: Int, dto: DepartmentDTO): DepartmentDTO? = dbQuery {
        val exists = Departments.selectAll().where { Departments.id eq id }.count() > 0
        if (!exists) return@dbQuery null

        Departments.update({ Departments.id eq id }) {
            it[name] = dto.name
            it[description] = dto.description
        }
        getById(id)
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Departments.deleteWhere { Departments.id eq id } > 0
    }

    private fun toDepartmentDTO(row: ResultRow): DepartmentDTO {
        return DepartmentDTO(
            id = row[Departments.id].value,
            name = row[Departments.name],
            description = row[Departments.description]
        )
    }
}

class PositionService {

    suspend fun getAll(): List<PositionDTO> = dbQuery {
        Positions.selectAll().map { toPositionDTO(it) }
    }

    suspend fun getById(id: Int): PositionDTO? = dbQuery {
        Positions.selectAll().where { Positions.id eq id }
            .map { toPositionDTO(it) }
            .singleOrNull()
    }

    suspend fun create(dto: PositionDTO): PositionDTO = dbQuery {
        val id = Positions.insertAndGetId {
            it[name] = dto.name
            it[description] = dto.description
        }
        getById(id.value)!!
    }

    suspend fun update(id: Int, dto: PositionDTO): PositionDTO? = dbQuery {
        val exists = Positions.selectAll().where { Positions.id eq id }.count() > 0
        if (!exists) return@dbQuery null

        Positions.update({ Positions.id eq id }) {
            it[name] = dto.name
            it[description] = dto.description
        }
        getById(id)
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Positions.deleteWhere { Positions.id eq id } > 0
    }

    private fun toPositionDTO(row: ResultRow): PositionDTO {
        return PositionDTO(
            id = row[Positions.id].value,
            name = row[Positions.name],
            description = row[Positions.description]
        )
    }
}

class ProductService {

    suspend fun getAll(): List<ProductDTO> = dbQuery {
        Products.selectAll().map { toProductDTO(it) }
    }

    suspend fun getAvailable(): List<ProductDTO> = dbQuery {
        Products.selectAll().where { Products.isAvailable eq true }
            .map { toProductDTO(it) }
    }

    suspend fun getById(id: Int): ProductDTO? = dbQuery {
        Products.selectAll().where { Products.id eq id }
            .map { toProductDTO(it) }
            .singleOrNull()
    }

    suspend fun create(dto: ProductDTO): ProductDTO = dbQuery {
        val id = Products.insertAndGetId {
            it[code] = dto.code
            it[name] = dto.name
            it[description] = dto.description
            it[price] = dto.price
            it[category] = dto.category
            it[isAvailable] = dto.isAvailable
        }
        getById(id.value)!!
    }

    suspend fun update(id: Int, dto: ProductDTO): ProductDTO? = dbQuery {
        val exists = Products.selectAll().where { Products.id eq id }.count() > 0
        if (!exists) return@dbQuery null

        Products.update({ Products.id eq id }) {
            it[code] = dto.code
            it[name] = dto.name
            it[description] = dto.description
            it[price] = dto.price
            it[category] = dto.category
            it[isAvailable] = dto.isAvailable
        }
        getById(id)
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Products.deleteWhere { Products.id eq id } > 0
    }

    private fun toProductDTO(row: ResultRow): ProductDTO {
        return ProductDTO(
            id = row[Products.id].value,
            code = row[Products.code],
            name = row[Products.name],
            description = row[Products.description],
            price = row[Products.price],
            category = row[Products.category],
            isAvailable = row[Products.isAvailable]
        )
    }
}
