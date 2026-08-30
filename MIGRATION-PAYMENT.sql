-- Migration: Create payment_transactions table for Razorpay integration
-- Run this before starting the application

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    razorpay_order_id VARCHAR(255) NOT NULL UNIQUE,
    razorpay_payment_id VARCHAR(255) UNIQUE,
    booking_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(1024),
    razorpay_signature VARCHAR(2048),
    paid_at DATETIME,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    INDEX idx_razorpay_order (razorpay_order_id),
    INDEX idx_razorpay_payment (razorpay_payment_id),
    INDEX idx_booking (booking_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Verification
SELECT COUNT(*) as transaction_count FROM payment_transactions;
