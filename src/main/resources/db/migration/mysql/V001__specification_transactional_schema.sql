-- AVAS transactional system of record, based on the detailed product/build specification.
-- Existing Hibernate-managed tables are intentionally preserved and referenced by logical IDs.

CREATE TABLE IF NOT EXISTS individual_profiles (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, user_id BINARY(16) NOT NULL,
    preferred_language VARCHAR(20), city VARCHAR(100), state_code VARCHAR(30), profile_data JSON,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_individual_profile_user (user_id), KEY idx_individual_profile_tenant (tenant_id)
);

CREATE TABLE IF NOT EXISTS builder_profiles (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, user_id BINARY(16) NOT NULL,
    business_name VARCHAR(190) NOT NULL, registration_number VARCHAR(100), verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    service_locations JSON, profile_data JSON,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_builder_profile_user (user_id), KEY idx_builder_profile_tenant_status (tenant_id, verification_status)
);

CREATE TABLE IF NOT EXISTS internal_user_profiles (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, user_id BINARY(16) NOT NULL,
    discipline VARCHAR(60), licence_number VARCHAR(100), verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING', profile_data JSON,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_internal_profile_user (user_id), KEY idx_internal_profile_tenant (tenant_id)
);

CREATE TABLE IF NOT EXISTS site_engineer_profiles (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, user_id BINARY(16) NOT NULL,
    licence_number VARCHAR(100), years_experience SMALLINT, verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING', profile_data JSON,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_engineer_profile_user (user_id), KEY idx_engineer_profile_tenant (tenant_id)
);

CREATE TABLE IF NOT EXISTS plot_details (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    address_line VARCHAR(255), city VARCHAR(100), state_code VARCHAR(30), postal_code VARCHAR(20), jurisdiction_code VARCHAR(80),
    plot_area DECIMAL(14,2), area_unit VARCHAR(20), frontage DECIMAL(12,2), depth DECIMAL(12,2), road_width DECIMAL(12,2),
    north_angle DECIMAL(7,3), geometry_document_id VARCHAR(80), detail_data JSON,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_plot_project (tenant_id, project_id), KEY idx_plot_jurisdiction (tenant_id, jurisdiction_code)
);

CREATE TABLE IF NOT EXISTS family_details (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    member_count SMALLINT NOT NULL, seniors_count SMALLINT NOT NULL DEFAULT 0, children_count SMALLINT NOT NULL DEFAULT 0,
    accessibility_needs JSON, lifestyle_preferences JSON,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_family_project (tenant_id, project_id)
);

CREATE TABLE IF NOT EXISTS structured_requirements (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    version_no INT NOT NULL, status VARCHAR(30) NOT NULL, source VARCHAR(30) NOT NULL, requirement_data JSON NOT NULL,
    interpretation_document_id VARCHAR(80), created_by BINARY(16),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_requirement_project_version (tenant_id, project_id, version_no), KEY idx_requirement_status (tenant_id, status)
);

CREATE TABLE IF NOT EXISTS drawing_metadata (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    drawing_type VARCHAR(50) NOT NULL, title VARCHAR(190) NOT NULL, current_version INT NOT NULL DEFAULT 1,
    lifecycle_status VARCHAR(30) NOT NULL, source VARCHAR(30) NOT NULL, created_by BINARY(16),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_drawing_metadata_project (tenant_id, project_id, drawing_type)
);

CREATE TABLE IF NOT EXISTS drawing_versions (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, drawing_id CHAR(36) NOT NULL,
    version_no INT NOT NULL, object_key VARCHAR(500), document_id VARCHAR(80), checksum CHAR(64), mime_type VARCHAR(100), byte_size BIGINT,
    generation_job_id CHAR(36), schema_version VARCHAR(30) NOT NULL, created_by BINARY(16),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_drawing_version (tenant_id, drawing_id, version_no), KEY idx_drawing_version_job (generation_job_id)
);

CREATE TABLE IF NOT EXISTS drawing_approvals (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    drawing_id CHAR(36) NOT NULL, drawing_version_id CHAR(36) NOT NULL, approval_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL, decision_by BINARY(16), decision_note TEXT, decided_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_drawing_approval_project (tenant_id, project_id, status)
);

CREATE TABLE IF NOT EXISTS building_rule_sets (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, code VARCHAR(100) NOT NULL, name VARCHAR(190) NOT NULL,
    authority_name VARCHAR(190), jurisdiction_code VARCHAR(80) NOT NULL, status VARCHAR(30) NOT NULL,
    effective_from DATE, effective_to DATE, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rule_set_code (tenant_id, code), KEY idx_rule_set_jurisdiction (tenant_id, jurisdiction_code, status)
);

CREATE TABLE IF NOT EXISTS building_rules (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, rule_set_id CHAR(36) NOT NULL,
    rule_code VARCHAR(120) NOT NULL, title VARCHAR(255) NOT NULL, rule_category VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL, description TEXT, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_building_rule_code (tenant_id, rule_set_id, rule_code), KEY idx_building_rule_category (tenant_id, rule_category)
);

CREATE TABLE IF NOT EXISTS building_rule_conditions (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, rule_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL, field_path VARCHAR(190) NOT NULL, operator_code VARCHAR(30) NOT NULL,
    comparison_value JSON, logical_join VARCHAR(10) NOT NULL DEFAULT 'AND',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_rule_condition (tenant_id, rule_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS building_rule_values (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, rule_id CHAR(36) NOT NULL,
    value_key VARCHAR(100) NOT NULL, numeric_value DECIMAL(18,4), text_value VARCHAR(500), json_value JSON, unit_code VARCHAR(30),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rule_value (tenant_id, rule_id, value_key)
);

CREATE TABLE IF NOT EXISTS rule_locations (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, rule_set_id CHAR(36) NOT NULL,
    country_code VARCHAR(3) NOT NULL, state_code VARCHAR(30), city VARCHAR(100), authority_code VARCHAR(80), postal_code_pattern VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_rule_location (tenant_id, country_code, state_code, city)
);

CREATE TABLE IF NOT EXISTS rule_versions (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, rule_set_id CHAR(36) NOT NULL,
    version_no INT NOT NULL, version_label VARCHAR(80) NOT NULL, status VARCHAR(30) NOT NULL,
    source_document_key VARCHAR(500), content_hash CHAR(64), published_at TIMESTAMP(6), published_by BINARY(16),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_rule_set_version (tenant_id, rule_set_id, version_no)
);

CREATE TABLE IF NOT EXISTS room_standards (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, room_type VARCHAR(80) NOT NULL,
    jurisdiction_code VARCHAR(80), minimum_area DECIMAL(12,2), minimum_width DECIMAL(12,2), preferred_aspect_ratio DECIMAL(8,4),
    standard_data JSON, version_label VARCHAR(80) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_room_standard_type (tenant_id, room_type, active)
);

CREATE TABLE IF NOT EXISTS construction_packages (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, code VARCHAR(100) NOT NULL, name VARCHAR(190) NOT NULL,
    category VARCHAR(60) NOT NULL, description TEXT, package_data JSON, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_construction_package (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS services (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, code VARCHAR(100) NOT NULL, name VARCHAR(190) NOT NULL,
    category VARCHAR(80) NOT NULL, unit_code VARCHAR(30), tax_code VARCHAR(50), active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_service_code (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS materials (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, sku VARCHAR(100) NOT NULL, name VARCHAR(190) NOT NULL,
    category VARCHAR(80) NOT NULL, brand VARCHAR(100), unit_code VARCHAR(30) NOT NULL, specification_data JSON,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_material_sku (tenant_id, sku)
);

CREATE TABLE IF NOT EXISTS prices (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, item_type VARCHAR(30) NOT NULL,
    material_id CHAR(36), service_id CHAR(36), city VARCHAR(100), currency_code CHAR(3) NOT NULL DEFAULT 'INR',
    unit_price DECIMAL(18,2) NOT NULL, valid_from DATE NOT NULL, valid_to DATE, evidence_status VARCHAR(30) NOT NULL,
    source_reference VARCHAR(500), submitted_by BINARY(16), approved_by BINARY(16),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_price_lookup (tenant_id, item_type, city, valid_from)
);

CREATE TABLE IF NOT EXISTS estimates (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    estimate_number VARCHAR(80) NOT NULL, version_no INT NOT NULL, status VARCHAR(30) NOT NULL,
    subtotal DECIMAL(18,2) NOT NULL DEFAULT 0, tax_total DECIMAL(18,2) NOT NULL DEFAULT 0,
    grand_total DECIMAL(18,2) NOT NULL DEFAULT 0, currency_code CHAR(3) NOT NULL DEFAULT 'INR',
    snapshot_document_id VARCHAR(80), approved_by BINARY(16), approved_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_estimate_number (tenant_id, estimate_number), KEY idx_estimate_project (tenant_id, project_id, version_no)
);

CREATE TABLE IF NOT EXISTS estimate_items (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, estimate_id CHAR(36) NOT NULL,
    item_code VARCHAR(100), description VARCHAR(500) NOT NULL, category VARCHAR(80) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL, unit_code VARCHAR(30) NOT NULL, unit_price DECIMAL(18,2) NOT NULL,
    line_total DECIMAL(18,2) NOT NULL, source_price_id CHAR(36), item_data JSON,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_estimate_item (tenant_id, estimate_id, category)
);

CREATE TABLE IF NOT EXISTS builder_quotations (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    builder_user_id BINARY(16) NOT NULL, estimate_id CHAR(36), quotation_number VARCHAR(80) NOT NULL,
    version_no INT NOT NULL, status VARCHAR(30) NOT NULL, subtotal DECIMAL(18,2) NOT NULL,
    tax_total DECIMAL(18,2) NOT NULL DEFAULT 0, grand_total DECIMAL(18,2) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'INR', valid_until DATE, terms TEXT,
    submitted_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_quotation_number (tenant_id, quotation_number), KEY idx_quotation_project (tenant_id, project_id, status)
);

CREATE TABLE IF NOT EXISTS builder_quotation_items (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, quotation_id CHAR(36) NOT NULL,
    estimate_item_id CHAR(36), description VARCHAR(500) NOT NULL, quantity DECIMAL(18,4) NOT NULL,
    unit_code VARCHAR(30) NOT NULL, unit_price DECIMAL(18,2) NOT NULL, line_total DECIMAL(18,2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_quotation_item (tenant_id, quotation_id)
);

CREATE TABLE IF NOT EXISTS assignments (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    assignment_type VARCHAR(50) NOT NULL, assignee_user_id BINARY(16) NOT NULL, assigned_by BINARY(16) NOT NULL,
    status VARCHAR(30) NOT NULL, starts_at TIMESTAMP(6), ends_at TIMESTAMP(6), assignment_data JSON,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_assignment_project (tenant_id, project_id, assignment_type, status), KEY idx_assignment_assignee (tenant_id, assignee_user_id, status)
);

CREATE TABLE IF NOT EXISTS milestones (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    sequence_no INT NOT NULL, code VARCHAR(100) NOT NULL, name VARCHAR(190) NOT NULL, status VARCHAR(30) NOT NULL,
    planned_start DATE, planned_end DATE, actual_start DATE, actual_end DATE, completion_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    amount_due DECIMAL(18,2), verified_by BINARY(16), verified_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_project_milestone (tenant_id, project_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS invoices (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    builder_user_id BINARY(16), milestone_id CHAR(36), invoice_number VARCHAR(80) NOT NULL, status VARCHAR(30) NOT NULL,
    subtotal DECIMAL(18,2) NOT NULL, tax_total DECIMAL(18,2) NOT NULL DEFAULT 0, grand_total DECIMAL(18,2) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'INR', due_date DATE, object_key VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_invoice_number (tenant_id, invoice_number), KEY idx_invoice_project (tenant_id, project_id, status)
);

CREATE TABLE IF NOT EXISTS invoice_items (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, invoice_id CHAR(36) NOT NULL,
    description VARCHAR(500) NOT NULL, hsn_sac_code VARCHAR(30), quantity DECIMAL(18,4) NOT NULL,
    unit_code VARCHAR(30) NOT NULL, unit_price DECIMAL(18,2) NOT NULL, tax_rate DECIMAL(7,4) NOT NULL DEFAULT 0,
    line_total DECIMAL(18,2) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_invoice_item (tenant_id, invoice_id)
);

CREATE TABLE IF NOT EXISTS approvals (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80),
    resource_type VARCHAR(60) NOT NULL, resource_id VARCHAR(80) NOT NULL, approval_type VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL, requested_by BINARY(16), decided_by BINARY(16), decision_note TEXT,
    requested_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), decided_at TIMESTAMP(6),
    KEY idx_approval_resource (tenant_id, resource_type, resource_id), KEY idx_approval_queue (tenant_id, approval_type, status)
);

CREATE TABLE IF NOT EXISTS professional_reviews (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60) NOT NULL, resource_id VARCHAR(80) NOT NULL, reviewer_user_id BINARY(16) NOT NULL,
    discipline VARCHAR(60) NOT NULL, status VARCHAR(30) NOT NULL, findings JSON, summary TEXT,
    reviewed_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_professional_review (tenant_id, project_id, resource_type, status)
);

CREATE TABLE IF NOT EXISTS generation_jobs (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, project_id VARCHAR(80) NOT NULL,
    job_type VARCHAR(60) NOT NULL, idempotency_key VARCHAR(120) NOT NULL, status VARCHAR(30) NOT NULL,
    request_document_id VARCHAR(80), result_document_id VARCHAR(80), attempt_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(80), error_message TEXT, started_at TIMESTAMP(6), completed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_generation_idempotency (tenant_id, idempotency_key), KEY idx_generation_job_queue (status, created_at)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, actor_user_id BINARY(16), actor_role VARCHAR(50),
    action_code VARCHAR(100) NOT NULL, resource_type VARCHAR(60) NOT NULL, resource_id VARCHAR(100),
    request_id VARCHAR(100), ip_address VARCHAR(64), before_data JSON, after_data JSON,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_audit_tenant_time (tenant_id, occurred_at),
    KEY idx_audit_resource (tenant_id, resource_type, resource_id)
);

CREATE TABLE IF NOT EXISTS transactional_outbox (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL, event_type VARCHAR(120) NOT NULL, event_version INT NOT NULL,
    payload JSON NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'PENDING', attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), published_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_outbox_dispatch (status, available_at)
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    id CHAR(36) PRIMARY KEY, tenant_id VARCHAR(60) NOT NULL, idempotency_key VARCHAR(120) NOT NULL,
    operation_code VARCHAR(100) NOT NULL, request_hash CHAR(64) NOT NULL, response_status INT,
    response_body JSON, resource_id VARCHAR(100), expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_idempotency_operation (tenant_id, operation_code, idempotency_key), KEY idx_idempotency_expiry (expires_at)
);
