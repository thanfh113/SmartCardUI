# SmartCard Backend API

Backend API server for SmartCard Employee Management System built with Ktor framework (Kotlin).

## Features

- Employee Management (CRUD operations)
- Wallet & Transactions (Top-up, Payment with RSA signature verification)
- Attendance Tracking (Check-in/Check-out with status calculation)
- Master Data Management (Departments, Positions, Products)
- SmartCard Integration (RSA signature verification, card sync)
- MySQL Database with Exposed ORM
- RESTful API with JSON responses

## Tech Stack

- **Framework**: Ktor 2.3.12
- **Language**: Kotlin 1.9+
- **Database**: MySQL 8.0+ with Exposed ORM
- **Security**: BCrypt for password hashing, BouncyCastle for RSA verification
- **Build Tool**: Gradle with Kotlin DSL
- **JDK**: 17+

## Prerequisites

Before running the backend, ensure you have:

1. **Java Development Kit (JDK) 17 or higher**
   ```bash
   java -version
   ```

2. **MySQL 8.0+** installed and running
   ```bash
   mysql --version
   ```

3. **DBeaver** (optional, for database management)
   - Download from: https://dbeaver.io/

## Database Setup

### 1. Install MySQL

**macOS (using Homebrew):**
```bash
brew install mysql
brew services start mysql
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install mysql-server
sudo systemctl start mysql
```

**Windows:**
- Download MySQL installer from: https://dev.mysql.com/downloads/installer/
- Follow the installation wizard

### 2. Create Database and User

```bash
# Login to MySQL
mysql -u root -p

# Create database
CREATE DATABASE smartcard_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Create user (optional, for production)
CREATE USER 'smartcard_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON smartcard_db.* TO 'smartcard_user'@'localhost';
FLUSH PRIVILEGES;

# Exit
EXIT;
```

### 3. Run Schema Migration

```bash
# From the backend directory
mysql -u root -p smartcard_db < src/main/sql/schema.sql
```

### 4. Using DBeaver (Optional)

1. Open DBeaver
2. Create new connection: Database → New Database Connection
3. Select MySQL
4. Configure:
   - Host: localhost
   - Port: 3306
   - Database: smartcard_db
   - Username: root (or your user)
   - Password: your password
5. Test connection and click Finish
6. Run the schema.sql script in DBeaver's SQL Editor

## Configuration

### application.conf

Located at: `backend/src/main/resources/application.conf`

```hocon
ktor {
  deployment {
    port = 8080
  }
  application {
    modules = [ com.smartcard.ApplicationKt.module ]
  }
}

database {
  url = "jdbc:mysql://localhost:3306/smartcard_db?useSSL=false&serverTimezone=UTC"
  driver = "com.mysql.cj.jdbc.Driver"
  user = "root"
  password = "password"  # Change this!
  maxPoolSize = 10
}
```

**Important**: Update the database password before running in production!

### Environment Variables (Optional)

You can override configuration using environment variables:

```bash
export DATABASE_URL="jdbc:mysql://localhost:3306/smartcard_db"
export DATABASE_USER="smartcard_user"
export DATABASE_PASSWORD="your_password"
export PORT=8080
```

## Building and Running

### Using Gradle Wrapper

**Build the project:**
```bash
./gradlew :backend:build
```

**Run the server:**
```bash
./gradlew :backend:run
```

**Or run with development mode:**
```bash
./gradlew :backend:run -Pdevelopment
```

### Using IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Navigate to `backend/src/main/kotlin/com/smartcard/Application.kt`
3. Click the green play button next to `fun main()`
4. Or right-click → Run 'ApplicationKt'

### Production Deployment

**Build executable JAR:**
```bash
./gradlew :backend:shadowJar
```

**Run the JAR:**
```bash
java -jar backend/build/libs/backend-1.0.0-all.jar
```

## API Endpoints

### Health Check

```
GET  /health         - Health check endpoint
GET  /               - API version info
```

### Employee Management

```
GET    /api/employees              - Get all employees
GET    /api/employees/{id}         - Get employee by ID
GET    /api/employees/card/{uuid}  - Get employee by card UUID
POST   /api/employees              - Create new employee
PUT    /api/employees/{id}         - Update employee
DELETE /api/employees/{id}         - Delete employee
POST   /api/employees/{id}/sync    - Sync card data
GET    /api/employees/{id}/balance - Get employee balance
```

### Transactions

```
POST   /api/transactions/topup     - Top-up employee wallet
POST   /api/transactions/payment   - Process payment (with RSA verification)
GET    /api/transactions/{emp_id}  - Get transaction history
```

### Attendance

```
POST   /api/attendance/checkin     - Check in
POST   /api/attendance/checkout    - Check out
GET    /api/attendance/{emp_id}    - Get attendance history
GET    /api/attendance/report      - Get attendance report (with filters)
```

### Master Data

```
GET/POST/PUT/DELETE  /api/departments  - Department CRUD
GET/POST/PUT/DELETE  /api/positions    - Position CRUD
GET/POST/PUT/DELETE  /api/products     - Product CRUD
```

## API Usage Examples

### Create Employee

```bash
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -d '{
    "cardUuid": "550e8400-e29b-41d4-a716-446655440000",
    "employeeId": "NV001",
    "name": "Nguyễn Văn A",
    "dateOfBirth": "1990-01-01",
    "departmentId": 1,
    "positionId": 4,
    "role": "USER"
  }'
```

### Top-up Balance

```bash
curl -X POST http://localhost:8080/api/transactions/topup \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": 1,
    "amount": 100000.00,
    "description": "Monthly allowance"
  }'
```

### Process Payment

```bash
curl -X POST http://localhost:8080/api/transactions/payment \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": 1,
    "productId": 1,
    "amount": 35000.00,
    "signature": "BASE64_ENCODED_RSA_SIGNATURE",
    "uniqueNumber": 12345,
    "timestamp": 1234567890
  }'
```

### Check In

```bash
curl -X POST http://localhost:8080/api/attendance/checkin \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": 1,
    "workDate": "2024-12-11",
    "checkInTime": "08:15:00"
  }'
```

## Testing with Postman

A Postman collection is available in `backend/postman/SmartCard-API.postman_collection.json`

**Import to Postman:**
1. Open Postman
2. Click Import
3. Select the collection file
4. Start testing the API

## SmartCard Integration

### RSA Signature Verification

The backend verifies RSA signatures from SmartCard during payment transactions:

1. **Card Format**: Public key is stored as `[Len Modulus][Modulus][Len Exponent][Exponent]`
2. **Transaction Message**: `[Employee ID (16)][Amount (4)][Timestamp (4)][Unique Number (4)]`
3. **Signature Algorithm**: SHA256withRSA

### Card Synchronization

The `/api/employees/{id}/sync` endpoint allows syncing:
- RSA public key from card
- Current balance
- Employee data

## Database Schema

Key tables:
- `departments` - Company departments
- `positions` - Employee positions
- `employees` - Employee records with card_uuid mapping
- `products` - Products for payment
- `transactions` - Transaction history (TOPUP/PAYMENT)
- `attendance_logs` - Daily attendance records

See `backend/src/main/sql/schema.sql` for complete schema.

## Security

### BCrypt Password Hashing

Employee PINs are hashed using BCrypt with cost factor 12:

```kotlin
val hashedPin = cryptoService.hashPin("1234")
val isValid = cryptoService.verifyPin("1234", hashedPin)
```

### RSA Signature Verification

Payments require valid RSA signatures from the card:

```kotlin
val isValid = cryptoService.verifyPaymentTransaction(
    signature, employeeId, amount, timestamp, uniqueNumber, publicKey
)
```

### CORS Configuration

CORS is enabled for all origins in development. **Restrict in production!**

Edit `backend/src/main/kotlin/com/smartcard/plugins/Security.kt`:

```kotlin
install(CORS) {
    allowHost("yourdomain.com")  // Restrict to your domain
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
}
```

## Logging

Logs are configured in `backend/src/main/resources/logback.xml`

- Console output: INFO level
- File output: `logs/smartcard.log` (DEBUG level)
- Rotation: Daily, kept for 30 days

## Troubleshooting

### Database Connection Errors

```
Error: Access denied for user 'root'@'localhost'
```

**Solution**: Check MySQL credentials in `application.conf`

### Port Already in Use

```
Error: Address already in use: bind
```

**Solution**: Change port in `application.conf` or kill the process using port 8080:

```bash
# Find process
lsof -i :8080

# Kill process
kill -9 <PID>
```

### Exposed ORM Errors

```
Error: Table doesn't exist
```

**Solution**: Run the schema migration script:

```bash
mysql -u root -p smartcard_db < src/main/sql/schema.sql
```

## Development

### Project Structure

```
backend/
├── src/main/
│   ├── kotlin/com/smartcard/
│   │   ├── Application.kt          # Main entry point
│   │   ├── plugins/                # Ktor plugins
│   │   │   ├── Database.kt
│   │   │   ├── Routing.kt
│   │   │   ├── Security.kt
│   │   │   └── Serialization.kt
│   │   ├── models/                 # Data models & DTOs
│   │   │   ├── Tables.kt
│   │   │   ├── DTOs.kt
│   │   │   └── Serializers.kt
│   │   ├── routes/                 # API routes
│   │   │   ├── EmployeeRoutes.kt
│   │   │   ├── TransactionRoutes.kt
│   │   │   ├── AttendanceRoutes.kt
│   │   │   └── MasterDataRoutes.kt
│   │   ├── services/               # Business logic
│   │   │   ├── EmployeeService.kt
│   │   │   ├── TransactionService.kt
│   │   │   ├── AttendanceService.kt
│   │   │   ├── CryptoService.kt
│   │   │   └── MasterDataServices.kt
│   │   └── data/                   # Database layer
│   │       └── DatabaseFactory.kt
│   ├── resources/
│   │   ├── application.conf        # Configuration
│   │   └── logback.xml            # Logging config
│   └── sql/
│       └── schema.sql              # Database schema
└── build.gradle.kts                # Build configuration
```

### Adding New Features

1. **Add new table**: Update `models/Tables.kt`
2. **Add DTO**: Update `models/DTOs.kt`
3. **Create service**: Add to `services/`
4. **Create routes**: Add to `routes/`
5. **Register routes**: Update `plugins/Routing.kt`

## License

This project is part of the SmartCard Employee Management System.

## Support

For issues or questions:
- Check the troubleshooting section
- Review the API documentation
- Check application logs in `logs/smartcard.log`

---

**Backend Version**: 1.0.0  
**Last Updated**: December 2024
