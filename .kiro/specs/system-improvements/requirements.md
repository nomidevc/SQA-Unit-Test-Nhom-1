# Requirements Document - Cải Tiến Hệ Thống TMDT

## Introduction

Tài liệu này mô tả các yêu cầu để cải tiến và hoàn thiện hệ thống TMDT hiện tại. Các cải tiến tập trung vào testing, bảo mật, performance, và các tính năng còn thiếu.

## Glossary

- **System**: Hệ thống thương mại điện tử hiện tại
- **Test Coverage**: Tỷ lệ code được test bao phủ
- **Property-Based Test**: Kiểm thử dựa trên thuộc tính tổng quát
- **N+1 Query**: Vấn đề performance khi query database nhiều lần không cần thiết
- **Webhook Signature**: Chữ ký xác thực webhook từ dịch vụ bên ngoài
- **Rate Limiting**: Giới hạn số lượng request trong khoảng thời gian
- **Audit Trail**: Lịch sử thay đổi dữ liệu (ai, khi nào, làm gì)
- **Idempotency**: Đảm bảo thao tác thực hiện nhiều lần cho kết quả giống nhau
- **Soft Delete**: Xóa logic (đánh dấu deleted) thay vì xóa vật lý

## Requirements

### Requirement 1: Comprehensive Testing Coverage

**User Story:** As a developer, I want comprehensive test coverage, so that I can ensure code quality and prevent regressions.

#### Acceptance Criteria

1. WHEN running unit tests, THE System SHALL achieve minimum 80% code coverage for service layer
2. WHEN running integration tests, THE System SHALL test all critical API endpoints with database interactions
3. WHEN running property-based tests, THE System SHALL validate business rules across random inputs (inventory calculations, financial calculations, date handling)
4. WHEN tests fail, THE System SHALL provide clear error messages indicating what went wrong
5. WHEN adding new features, THE System SHALL require tests before merging code

### Requirement 2: Webhook Security Enhancement

**User Story:** As a system administrator, I want secure webhook endpoints, so that only legitimate requests are processed.

#### Acceptance Criteria

1. WHEN receiving GHN webhook, THE System SHALL verify signature using HMAC-SHA256 before processing
2. WHEN receiving SePay webhook, THE System SHALL verify signature using configured secret key
3. WHEN webhook signature is invalid, THE System SHALL reject request with HTTP 401 and log security event
4. WHEN webhook is duplicate (same transaction ID), THE System SHALL return HTTP 200 without reprocessing (idempotency)
5. WHEN webhook processing fails, THE System SHALL return HTTP 500 to trigger retry from external service

### Requirement 3: Input Validation Enhancement

**User Story:** As a developer, I want comprehensive input validation, so that invalid data is rejected early.

#### Acceptance Criteria

1. WHEN receiving API request, THE System SHALL validate all DTO fields using Bean Validation annotations
2. WHEN validation fails, THE System SHALL return HTTP 400 with specific field errors
3. WHEN uploading files, THE System SHALL validate file size (max 5MB), format (jpg, png, webp), and dimensions
4. WHEN creating order, THE System SHALL validate email format, phone format, and address completeness
5. WHEN importing Excel, THE System SHALL validate all required columns and data types before processing

### Requirement 4: Performance Optimization

**User Story:** As a user, I want fast response times, so that I can work efficiently.

#### Acceptance Criteria

1. WHEN loading dashboard, THE System SHALL execute maximum 5 database queries using JOIN FETCH
2. WHEN searching products, THE System SHALL use database-level filtering instead of in-memory filtering
3. WHEN accessing static data (provinces, categories), THE System SHALL serve from cache with 1-hour TTL
4. WHEN querying frequently accessed tables, THE System SHALL use appropriate indexes (response time < 100ms)
5. WHEN loading large datasets, THE System SHALL implement pagination with configurable page size

### Requirement 5: Database Index Optimization

**User Story:** As a database administrator, I want optimized indexes, so that queries execute quickly.

#### Acceptance Criteria

1. WHEN querying orders by customer, THE System SHALL use index on customer_id column
2. WHEN querying orders by status, THE System SHALL use index on status column
3. WHEN querying products by category, THE System SHALL use index on category_id column
4. WHEN querying financial transactions by date range, THE System SHALL use index on transaction_date column
5. WHEN analyzing index usage, THE System SHALL show all critical queries using indexes (EXPLAIN ANALYZE)

### Requirement 6: External API Resilience

**User Story:** As a system administrator, I want resilient external API integration, so that temporary failures don't break the system.

#### Acceptance Criteria

1. WHEN GHN API call fails, THE System SHALL retry up to 3 times with exponential backoff (1s, 2s, 4s)
2. WHEN external API times out, THE System SHALL fail gracefully and return user-friendly error message
3. WHEN rate limit is exceeded, THE System SHALL queue requests and retry after cooldown period
4. WHEN external service is unavailable, THE System SHALL log error and notify administrators
5. WHEN API call succeeds after retry, THE System SHALL log retry count for monitoring

### Requirement 7: Caching Strategy

**User Story:** As a developer, I want intelligent caching, so that frequently accessed data loads quickly.

#### Acceptance Criteria

1. WHEN fetching GHN provinces/districts/wards, THE System SHALL cache results for 24 hours
2. WHEN fetching product categories, THE System SHALL cache results for 1 hour
3. WHEN updating cached data, THE System SHALL invalidate cache automatically
4. WHEN cache is unavailable, THE System SHALL fall back to database without errors
5. WHEN viewing cache statistics, THE System SHALL show hit rate and miss rate per cache region

### Requirement 8: API Documentation

**User Story:** As a frontend developer, I want complete API documentation, so that I can integrate endpoints correctly.

#### Acceptance Criteria

1. WHEN accessing Swagger UI, THE System SHALL display all endpoints with descriptions
2. WHEN viewing endpoint documentation, THE System SHALL show request/response examples
3. WHEN endpoint requires authentication, THE System SHALL indicate required roles/permissions
4. WHEN endpoint can return errors, THE System SHALL document all possible error codes
5. WHEN API changes, THE System SHALL update documentation automatically from code annotations

### Requirement 9: Audit Trail Implementation

**User Story:** As an auditor, I want complete audit trail, so that I can track all data changes.

#### Acceptance Criteria

1. WHEN creating/updating critical records (orders, payments, inventory), THE System SHALL record created_by and updated_by
2. WHEN viewing audit log, THE System SHALL display who made changes, when, and what changed
3. WHEN deleting records, THE System SHALL use soft delete and record deleted_by and deleted_at
4. WHEN audit log is queried, THE System SHALL support filtering by user, date range, and entity type
5. WHEN audit log reaches size limit, THE System SHALL archive old records (older than 1 year)

### Requirement 10: Rate Limiting

**User Story:** As a system administrator, I want rate limiting, so that API abuse is prevented.

#### Acceptance Criteria

1. WHEN user makes login attempts, THE System SHALL limit to 5 attempts per 15 minutes per IP
2. WHEN user makes API calls, THE System SHALL limit to 100 requests per minute per user
3. WHEN rate limit is exceeded, THE System SHALL return HTTP 429 with Retry-After header
4. WHEN viewing rate limit status, THE System SHALL show remaining quota in response headers
5. WHEN rate limit is configured, THE System SHALL support different limits per endpoint and role

### Requirement 11: Batch Operations

**User Story:** As an administrator, I want batch operations, so that I can process multiple items efficiently.

#### Acceptance Criteria

1. WHEN importing products via Excel, THE System SHALL process up to 1000 products in single transaction
2. WHEN updating order statuses, THE System SHALL support bulk update with validation
3. WHEN exporting data, THE System SHALL generate Excel/CSV files with all selected records
4. WHEN batch operation fails, THE System SHALL rollback all changes and report specific errors
5. WHEN batch operation is large, THE System SHALL process in chunks and show progress indicator

### Requirement 12: Advanced Search and Filtering

**User Story:** As a user, I want advanced search, so that I can find data quickly.

#### Acceptance Criteria

1. WHEN searching products, THE System SHALL support filtering by category, price range, supplier, and stock status
2. WHEN searching orders, THE System SHALL support filtering by status, date range, customer, and payment method
3. WHEN applying multiple filters, THE System SHALL combine them with AND logic
4. WHEN saving search filters, THE System SHALL allow users to reuse saved searches
5. WHEN search returns many results, THE System SHALL paginate with configurable page size

### Requirement 13: Export Functionality

**User Story:** As a manager, I want to export reports, so that I can analyze data offline.

#### Acceptance Criteria

1. WHEN exporting inventory report, THE System SHALL generate Excel file with all products and stock levels
2. WHEN exporting financial report, THE System SHALL generate PDF with charts and summary tables
3. WHEN exporting order list, THE System SHALL generate CSV with all order details
4. WHEN export is large, THE System SHALL process asynchronously and notify when ready
5. WHEN export file is generated, THE System SHALL provide download link valid for 24 hours

### Requirement 14: Real-time Notifications

**User Story:** As a user, I want real-time notifications, so that I'm informed of important events immediately.

#### Acceptance Criteria

1. WHEN order status changes, THE System SHALL send real-time notification to relevant users via WebSocket
2. WHEN inventory reaches low stock threshold, THE System SHALL notify warehouse staff
3. WHEN payment is received, THE System SHALL notify sales staff and accountant
4. WHEN viewing notifications, THE System SHALL show unread count and allow marking as read
5. WHEN user is offline, THE System SHALL queue notifications and deliver when user reconnects

### Requirement 15: Error Handling Standardization

**User Story:** As a developer, I want standardized error handling, so that errors are consistent and informative.

#### Acceptance Criteria

1. WHEN business rule violation occurs, THE System SHALL throw specific exception (e.g., InsufficientStockException)
2. WHEN handling exceptions, THE System SHALL use @RestControllerAdvice for global error handling
3. WHEN returning errors, THE System SHALL use consistent format (status, message, timestamp, path)
4. WHEN logging errors, THE System SHALL include stack trace for 5xx errors but not 4xx errors
5. WHEN error occurs in transaction, THE System SHALL rollback database changes automatically

### Requirement 16: Database Schema Enhancements

**User Story:** As a database administrator, I want robust schema, so that data integrity is enforced.

#### Acceptance Criteria

1. WHEN inserting records, THE System SHALL enforce NOT NULL constraints on critical columns
2. WHEN inserting duplicate data, THE System SHALL enforce UNIQUE constraints (e.g., supplier tax_code, product SKU)
3. WHEN inserting invalid data, THE System SHALL enforce CHECK constraints (e.g., paid_amount <= total_amount)
4. WHEN deleting records, THE System SHALL use soft delete (deleted_at column) for critical tables
5. WHEN querying data, THE System SHALL exclude soft-deleted records by default

### Requirement 17: Inventory Forecasting

**User Story:** As a warehouse manager, I want inventory forecasting, so that I can plan purchases proactively.

#### Acceptance Criteria

1. WHEN viewing product inventory, THE System SHALL display average daily sales rate
2. WHEN stock level is low, THE System SHALL calculate estimated days until stockout
3. WHEN generating reorder report, THE System SHALL suggest reorder quantity based on lead time and sales rate
4. WHEN viewing forecast, THE System SHALL show confidence level based on historical data quality
5. WHEN sales pattern changes, THE System SHALL adjust forecast automatically

### Requirement 18: Payment Plan Support

**User Story:** As a sales staff, I want to offer payment plans, so that customers can pay in installments.

#### Acceptance Criteria

1. WHEN creating order, THE System SHALL allow selecting payment plan (full, 2 installments, 3 installments)
2. WHEN payment plan is selected, THE System SHALL calculate installment amounts and due dates
3. WHEN installment is due, THE System SHALL send reminder notification to customer
4. WHEN installment is paid, THE System SHALL update payment status and send receipt
5. WHEN payment plan is overdue, THE System SHALL flag order and notify collections team

### Requirement 19: Multi-Currency Support

**User Story:** As an administrator, I want multi-currency support, so that international transactions are supported.

#### Acceptance Criteria

1. WHEN creating product, THE System SHALL allow setting price in multiple currencies (VND, USD, EUR)
2. WHEN displaying prices, THE System SHALL show in user's preferred currency
3. WHEN processing payment, THE System SHALL convert to base currency (VND) using current exchange rate
4. WHEN viewing financial reports, THE System SHALL support currency conversion with historical rates
5. WHEN exchange rate changes, THE System SHALL update rates daily from external API

### Requirement 20: Advanced Analytics Dashboard

**User Story:** As a manager, I want advanced analytics, so that I can make data-driven decisions.

#### Acceptance Criteria

1. WHEN viewing dashboard, THE System SHALL display revenue trends with charts (daily, weekly, monthly)
2. WHEN analyzing products, THE System SHALL show top-selling products and slow-moving inventory
3. WHEN analyzing customers, THE System SHALL show customer lifetime value and retention rate
4. WHEN analyzing suppliers, THE System SHALL show supplier performance metrics (on-time delivery, quality)
5. WHEN exporting analytics, THE System SHALL generate executive summary report in PDF format

