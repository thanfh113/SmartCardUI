-- SmartCard Database Schema
-- MySQL 8.0+

-- Create database
CREATE DATABASE IF NOT EXISTS smartcard_db 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE smartcard_db;

-- Drop tables if they exist (for clean reinstall)
DROP TABLE IF EXISTS attendance_logs;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS positions;
DROP TABLE IF EXISTS departments;

-- Create ENUM types (MySQL uses ENUM in table definition)

-- ===================================
-- DEPARTMENTS TABLE
-- ===================================
CREATE TABLE departments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================
-- POSITIONS TABLE
-- ===================================
CREATE TABLE positions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pos_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================
-- EMPLOYEES TABLE
-- ===================================
CREATE TABLE employees (
    id INT AUTO_INCREMENT PRIMARY KEY,
    card_uuid VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID from SmartCard (36 chars)',
    employee_id VARCHAR(20) NOT NULL UNIQUE COMMENT 'Employee ID (maNV)',
    name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    department_id INT NOT NULL,
    position_id INT NOT NULL,
    role ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    photo_path VARCHAR(255),
    rsa_public_key BLOB COMMENT 'RSA public key from card',
    pin_hash VARCHAR(255) COMMENT 'BCrypt hash of PIN',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT,
    FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE RESTRICT,
    
    INDEX idx_emp_card_uuid (card_uuid),
    INDEX idx_emp_employee_id (employee_id),
    INDEX idx_emp_name (name),
    INDEX idx_emp_dept (department_id),
    INDEX idx_emp_pos (position_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================
-- PRODUCTS TABLE
-- ===================================
CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    category VARCHAR(50),
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_prod_code (code),
    INDEX idx_prod_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================
-- TRANSACTIONS TABLE
-- ===================================
CREATE TABLE transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    employee_id INT NOT NULL,
    trans_type ENUM('TOPUP', 'PAYMENT') NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    balance_before DECIMAL(15, 2) NOT NULL,
    balance_after DECIMAL(15, 2) NOT NULL,
    description TEXT,
    product_id INT NULL COMMENT 'Product ID for PAYMENT transactions',
    signature BLOB COMMENT 'RSA signature from card for PAYMENT',
    unique_number INT COMMENT 'Unique number from transaction for verification',
    transaction_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL,
    
    INDEX idx_trans_emp (employee_id),
    INDEX idx_trans_type (trans_type),
    INDEX idx_trans_time (transaction_time),
    INDEX idx_trans_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================
-- ATTENDANCE_LOGS TABLE
-- ===================================
CREATE TABLE attendance_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    employee_id INT NOT NULL,
    work_date DATE NOT NULL,
    check_in_time TIME,
    check_out_time TIME,
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, ON_TIME, LATE, ABSENT',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    
    UNIQUE KEY uk_emp_date (employee_id, work_date),
    INDEX idx_att_emp (employee_id),
    INDEX idx_att_date (work_date),
    INDEX idx_att_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================
-- SEED DATA (Optional)
-- ===================================

-- Insert sample departments
INSERT INTO departments (name, description) VALUES
    ('Phòng Kỹ Thuật', 'Phòng phát triển kỹ thuật và công nghệ'),
    ('Phòng Nhân Sự', 'Phòng quản lý nhân sự và tuyển dụng'),
    ('Phòng Kinh Doanh', 'Phòng kinh doanh và marketing'),
    ('Phòng Kế Toán', 'Phòng kế toán và tài chính'),
    ('Ban Giám Đốc', 'Ban lãnh đạo công ty');

-- Insert sample positions
INSERT INTO positions (name, description) VALUES
    ('Giám Đốc', 'Giám đốc điều hành'),
    ('Trưởng Phòng', 'Trưởng phòng ban'),
    ('Phó Phòng', 'Phó trưởng phòng'),
    ('Nhân Viên', 'Nhân viên chính thức'),
    ('Thực Tập Sinh', 'Sinh viên thực tập');

-- Insert sample products
INSERT INTO products (code, name, description, price, category) VALUES
    ('FOOD001', 'Cơm Sườn', 'Cơm sườn nướng', 35000.00, 'Món Chính'),
    ('FOOD002', 'Cơm Gà', 'Cơm gà xối mỡ', 35000.00, 'Món Chính'),
    ('FOOD003', 'Phở Bò', 'Phở bò tái', 40000.00, 'Món Chính'),
    ('DRINK001', 'Nước Ngọt', 'Coca Cola/Pepsi', 10000.00, 'Đồ Uống'),
    ('DRINK002', 'Trà Đá', 'Trà đá miễn phí', 0.00, 'Đồ Uống'),
    ('SNACK001', 'Bánh Mì', 'Bánh mì pate', 15000.00, 'Ăn Vặt');

-- Note: Employee data should be added when cards are registered
-- Example employee insert (replace with actual card UUID):
-- INSERT INTO employees (card_uuid, employee_id, name, date_of_birth, department_id, position_id, role, balance)
-- VALUES ('550e8400-e29b-41d4-a716-446655440000', 'NV001', 'Nguyễn Văn A', '1990-01-01', 1, 4, 'USER', 100000.00);

COMMIT;
