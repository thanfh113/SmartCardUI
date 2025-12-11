# SmartCard Backend - Standalone Setup Guide

## Hướng dẫn Copy Backend sang Project Ktor Khác

### Cách 1: Standalone Project (ĐƠN GIẢN NHẤT - KHUYÊN DÙNG)

Backend này đã được chuẩn bị để chạy độc lập, bạn chỉ cần làm theo các bước sau:

#### Bước 1: Copy Files
Copy toàn bộ thư mục `backend/` sang vị trí mới (ví dụ: `/path/to/smartcard-backend/`)

#### Bước 2: Setup Standalone Config
```bash
cd /path/to/smartcard-backend/

# Đổi tên file standalone thành file chính
mv build.gradle.kts.standalone build.gradle.kts
mv settings.gradle.kts.standalone settings.gradle.kts
```

#### Bước 3: Setup Database
```bash
# Tạo database trong MySQL
mysql -u root -p
CREATE DATABASE smartcard_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;

# Import schema
mysql -u root -p smartcard_db < src/main/sql/schema.sql
```

#### Bước 4: Cấu hình Database Connection
Sửa file `src/main/resources/application.conf`:
```hocon
database {
  url = "jdbc:mysql://localhost:3306/smartcard_db?useSSL=false&serverTimezone=UTC"
  driver = "com.mysql.cj.jdbc.Driver"
  user = "root"          # Đổi thành user của bạn
  password = "password"   # Đổi thành password của bạn
  maxPoolSize = 10
}
```

#### Bước 5: Build và Run
```bash
# Build project
./gradlew build

# Run server
./gradlew run

# Hoặc run với development mode
./gradlew run -Pdevelopment
```

Server sẽ chạy tại: `http://localhost:8080`

#### Bước 6: Test API
- Import Postman collection từ `postman/SmartCard-API.postman_collection.json`
- Hoặc test bằng curl:
```bash
# Health check
curl http://localhost:8080/health

# Get all departments
curl http://localhost:8080/api/departments
```

---

### Cách 2: Thêm vào Project Ktor Có Sẵn (Multi-module)

Nếu bạn muốn thêm backend này vào một Ktor project có sẵn:

#### Bước 1: Copy Files
```bash
# Copy thư mục backend vào project
cp -r backend /path/to/your-ktor-project/backend
```

#### Bước 2: Update settings.gradle.kts
Thêm vào file `settings.gradle.kts` của project:
```kotlin
include(":backend")
```

#### Bước 3: Chọn một trong hai cách config dependencies:

**Option A: Dùng version catalog của project hiện tại**
- Giữ nguyên `build.gradle.kts` (sử dụng `libs.*`)
- Merge nội dung từ `gradle/libs.versions.toml` vào version catalog của project

**Option B: Dùng standalone config**
- Đổi tên: `mv build.gradle.kts.standalone build.gradle.kts`
- Xóa file `settings.gradle.kts.standalone` (không cần)

#### Bước 4: Build
```bash
./gradlew :backend:build
./gradlew :backend:run
```

---

### Cách 3: Chỉ Copy Source Code

Nếu chỉ muốn copy source code vào project Ktor có sẵn:

#### Files cần copy:
```
src/main/kotlin/com/smartcard/    → Copy vào src/main/kotlin/ của project
src/main/resources/                → Merge với resources/ của project
src/main/sql/schema.sql           → Copy vào project
```

#### Dependencies cần thêm vào build.gradle.kts:
```kotlin
dependencies {
    // Ktor (nếu chưa có)
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")
    implementation("io.ktor:ktor-server-status-pages:2.3.12")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.55.0")
    implementation("com.mysql:mysql-connector-j:8.0.33")
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // Security
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
}
```

#### Update Application.kt của project:
```kotlin
fun Application.module() {
    configureDatabase()      // Thêm dòng này
    configureSerialization()
    configureSecurity()
    configureRouting()
}
```

---

## Troubleshooting

### Lỗi: "Plugin org.jetbrains.kotlin.jvm already on classpath"
**Giải pháp**: Dùng file `build.gradle.kts.standalone` đã được chuẩn bị sẵn

### Lỗi: "Table doesn't exist"
**Giải pháp**: Import schema.sql vào database:
```bash
mysql -u root -p smartcard_db < src/main/sql/schema.sql
```

### Lỗi: "Access denied for user"
**Giải pháp**: Kiểm tra lại user/password trong `application.conf`

### Port 8080 đã được sử dụng
**Giải pháp**: Đổi port trong `application.conf`:
```hocon
ktor {
  deployment {
    port = 8081  # Đổi sang port khác
  }
}
```

---

## Lưu ý quan trọng

1. **JDK Version**: Project cần JDK 17 trở lên
   ```bash
   java -version  # Kiểm tra version
   ```

2. **MySQL Version**: Cần MySQL 8.0+
   ```bash
   mysql --version  # Kiểm tra version
   ```

3. **Gradle Wrapper**: Project đã có sẵn gradlew, không cần cài Gradle

4. **Database Schema**: Nhớ import schema.sql trước khi chạy server

5. **Configuration**: Luôn kiểm tra và update `application.conf` với thông tin database của bạn

---

## Kết luận

**Khuyến nghị**: Dùng **Cách 1 (Standalone Project)** vì:
- ✅ Đơn giản nhất
- ✅ Không phụ thuộc vào project khác
- ✅ Dễ debug
- ✅ Có thể chạy ngay lập tức

Chỉ cần:
1. Copy thư mục backend
2. Đổi tên 2 file .standalone
3. Config database
4. Run!

---

**Cần hỗ trợ thêm?** 
- Xem README.md trong thư mục backend
- Check IMPLEMENTATION_SUMMARY.md để hiểu chi tiết implementation
- Import Postman collection để test API
