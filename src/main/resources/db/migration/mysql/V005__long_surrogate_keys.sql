-- AVAS V005: BIGINT surrogate keys for the specification transactional schema.
--
-- Every table below moves from a CHAR(36) UUID primary key to a BIGINT AUTO_INCREMENT
-- surrogate, matching AbstractLongIdEntity, which is how the application already maps its
-- own tables. The original UUID is retained as public_id so any external reference that
-- was handed out remains resolvable; dropping it would make that correlation unrecoverable.
--
-- Foreign keys are remapped by joining on the retained public_id before the CHAR(36)
-- column is dropped, so the operation is order-dependent and must run as one unit.

-- 1. Give every table a BIGINT identity, keeping the UUID as public_id.
ALTER TABLE approvals CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE approvals DROP PRIMARY KEY;
ALTER TABLE approvals ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE approvals ADD UNIQUE KEY uk_approvals_public_id (public_id);

ALTER TABLE assignments CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE assignments DROP PRIMARY KEY;
ALTER TABLE assignments ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE assignments ADD UNIQUE KEY uk_assignments_public_id (public_id);

ALTER TABLE audit_logs CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE audit_logs DROP PRIMARY KEY;
ALTER TABLE audit_logs ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE audit_logs ADD UNIQUE KEY uk_audit_logs_public_id (public_id);

ALTER TABLE builder_profiles CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE builder_profiles DROP PRIMARY KEY;
ALTER TABLE builder_profiles ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE builder_profiles ADD UNIQUE KEY uk_builder_profiles_public_id (public_id);

ALTER TABLE builder_quotation_items CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE builder_quotation_items DROP PRIMARY KEY;
ALTER TABLE builder_quotation_items ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE builder_quotation_items ADD UNIQUE KEY uk_builder_quotation_items_public_id (public_id);

ALTER TABLE builder_quotations CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE builder_quotations DROP PRIMARY KEY;
ALTER TABLE builder_quotations ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE builder_quotations ADD UNIQUE KEY uk_builder_quotations_public_id (public_id);

ALTER TABLE building_rule_conditions CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE building_rule_conditions DROP PRIMARY KEY;
ALTER TABLE building_rule_conditions ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE building_rule_conditions ADD UNIQUE KEY uk_building_rule_conditions_public_id (public_id);

ALTER TABLE building_rule_sets CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE building_rule_sets DROP PRIMARY KEY;
ALTER TABLE building_rule_sets ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE building_rule_sets ADD UNIQUE KEY uk_building_rule_sets_public_id (public_id);

ALTER TABLE building_rule_values CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE building_rule_values DROP PRIMARY KEY;
ALTER TABLE building_rule_values ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE building_rule_values ADD UNIQUE KEY uk_building_rule_values_public_id (public_id);

ALTER TABLE building_rules CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE building_rules DROP PRIMARY KEY;
ALTER TABLE building_rules ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE building_rules ADD UNIQUE KEY uk_building_rules_public_id (public_id);

ALTER TABLE construction_packages CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE construction_packages DROP PRIMARY KEY;
ALTER TABLE construction_packages ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE construction_packages ADD UNIQUE KEY uk_construction_packages_public_id (public_id);

ALTER TABLE drawing_approvals CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE drawing_approvals DROP PRIMARY KEY;
ALTER TABLE drawing_approvals ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE drawing_approvals ADD UNIQUE KEY uk_drawing_approvals_public_id (public_id);

ALTER TABLE drawing_metadata CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE drawing_metadata DROP PRIMARY KEY;
ALTER TABLE drawing_metadata ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE drawing_metadata ADD UNIQUE KEY uk_drawing_metadata_public_id (public_id);

ALTER TABLE drawing_versions CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE drawing_versions DROP PRIMARY KEY;
ALTER TABLE drawing_versions ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE drawing_versions ADD UNIQUE KEY uk_drawing_versions_public_id (public_id);

ALTER TABLE estimate_items CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE estimate_items DROP PRIMARY KEY;
ALTER TABLE estimate_items ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE estimate_items ADD UNIQUE KEY uk_estimate_items_public_id (public_id);

ALTER TABLE estimates CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE estimates DROP PRIMARY KEY;
ALTER TABLE estimates ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE estimates ADD UNIQUE KEY uk_estimates_public_id (public_id);

ALTER TABLE family_details CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE family_details DROP PRIMARY KEY;
ALTER TABLE family_details ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE family_details ADD UNIQUE KEY uk_family_details_public_id (public_id);

ALTER TABLE generation_jobs CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE generation_jobs DROP PRIMARY KEY;
ALTER TABLE generation_jobs ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE generation_jobs ADD UNIQUE KEY uk_generation_jobs_public_id (public_id);

ALTER TABLE idempotency_records CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE idempotency_records DROP PRIMARY KEY;
ALTER TABLE idempotency_records ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE idempotency_records ADD UNIQUE KEY uk_idempotency_records_public_id (public_id);

ALTER TABLE individual_profiles CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE individual_profiles DROP PRIMARY KEY;
ALTER TABLE individual_profiles ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE individual_profiles ADD UNIQUE KEY uk_individual_profiles_public_id (public_id);

ALTER TABLE internal_user_profiles CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE internal_user_profiles DROP PRIMARY KEY;
ALTER TABLE internal_user_profiles ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE internal_user_profiles ADD UNIQUE KEY uk_internal_user_profiles_public_id (public_id);

ALTER TABLE invoice_items CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE invoice_items DROP PRIMARY KEY;
ALTER TABLE invoice_items ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE invoice_items ADD UNIQUE KEY uk_invoice_items_public_id (public_id);

ALTER TABLE invoices CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE invoices DROP PRIMARY KEY;
ALTER TABLE invoices ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE invoices ADD UNIQUE KEY uk_invoices_public_id (public_id);

ALTER TABLE materials CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE materials DROP PRIMARY KEY;
ALTER TABLE materials ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE materials ADD UNIQUE KEY uk_materials_public_id (public_id);

ALTER TABLE milestones CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE milestones DROP PRIMARY KEY;
ALTER TABLE milestones ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE milestones ADD UNIQUE KEY uk_milestones_public_id (public_id);

ALTER TABLE plot_details CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE plot_details DROP PRIMARY KEY;
ALTER TABLE plot_details ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE plot_details ADD UNIQUE KEY uk_plot_details_public_id (public_id);

ALTER TABLE prices CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE prices DROP PRIMARY KEY;
ALTER TABLE prices ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE prices ADD UNIQUE KEY uk_prices_public_id (public_id);

ALTER TABLE professional_reviews CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE professional_reviews DROP PRIMARY KEY;
ALTER TABLE professional_reviews ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE professional_reviews ADD UNIQUE KEY uk_professional_reviews_public_id (public_id);

ALTER TABLE room_standards CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE room_standards DROP PRIMARY KEY;
ALTER TABLE room_standards ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE room_standards ADD UNIQUE KEY uk_room_standards_public_id (public_id);

ALTER TABLE rule_locations CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE rule_locations DROP PRIMARY KEY;
ALTER TABLE rule_locations ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE rule_locations ADD UNIQUE KEY uk_rule_locations_public_id (public_id);

ALTER TABLE rule_versions CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE rule_versions DROP PRIMARY KEY;
ALTER TABLE rule_versions ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE rule_versions ADD UNIQUE KEY uk_rule_versions_public_id (public_id);

ALTER TABLE services CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE services DROP PRIMARY KEY;
ALTER TABLE services ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE services ADD UNIQUE KEY uk_services_public_id (public_id);

ALTER TABLE site_engineer_profiles CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE site_engineer_profiles DROP PRIMARY KEY;
ALTER TABLE site_engineer_profiles ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE site_engineer_profiles ADD UNIQUE KEY uk_site_engineer_profiles_public_id (public_id);

ALTER TABLE structured_requirements CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE structured_requirements DROP PRIMARY KEY;
ALTER TABLE structured_requirements ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE structured_requirements ADD UNIQUE KEY uk_structured_requirements_public_id (public_id);

ALTER TABLE transactional_outbox CHANGE COLUMN id public_id CHAR(36) NOT NULL;
ALTER TABLE transactional_outbox DROP PRIMARY KEY;
ALTER TABLE transactional_outbox ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE transactional_outbox ADD UNIQUE KEY uk_transactional_outbox_public_id (public_id);

-- 2. Add BIGINT foreign keys, populate them from the retained UUIDs, then retire the CHAR(36).
ALTER TABLE builder_quotation_items CHANGE COLUMN estimate_item_id estimate_item_id_uuid CHAR(36) NULL;
ALTER TABLE builder_quotation_items ADD COLUMN estimate_item_id BIGINT UNSIGNED NULL;
UPDATE builder_quotation_items c JOIN estimate_items p ON c.estimate_item_id_uuid = p.public_id SET c.estimate_item_id = p.id;
ALTER TABLE builder_quotation_items DROP COLUMN estimate_item_id_uuid;
ALTER TABLE builder_quotation_items ADD KEY idx_builder_quotation_items_estimate_item_id (estimate_item_id);

ALTER TABLE builder_quotation_items CHANGE COLUMN quotation_id quotation_id_uuid CHAR(36) NULL;
ALTER TABLE builder_quotation_items ADD COLUMN quotation_id BIGINT UNSIGNED NULL;
UPDATE builder_quotation_items c JOIN builder_quotations p ON c.quotation_id_uuid = p.public_id SET c.quotation_id = p.id;
ALTER TABLE builder_quotation_items DROP COLUMN quotation_id_uuid;
ALTER TABLE builder_quotation_items ADD KEY idx_builder_quotation_items_quotation_id (quotation_id);

ALTER TABLE builder_quotations CHANGE COLUMN estimate_id estimate_id_uuid CHAR(36) NULL;
ALTER TABLE builder_quotations ADD COLUMN estimate_id BIGINT UNSIGNED NULL;
UPDATE builder_quotations c JOIN estimates p ON c.estimate_id_uuid = p.public_id SET c.estimate_id = p.id;
ALTER TABLE builder_quotations DROP COLUMN estimate_id_uuid;
ALTER TABLE builder_quotations ADD KEY idx_builder_quotations_estimate_id (estimate_id);

ALTER TABLE building_rule_conditions CHANGE COLUMN rule_id rule_id_uuid CHAR(36) NULL;
ALTER TABLE building_rule_conditions ADD COLUMN rule_id BIGINT UNSIGNED NULL;
UPDATE building_rule_conditions c JOIN building_rules p ON c.rule_id_uuid = p.public_id SET c.rule_id = p.id;
ALTER TABLE building_rule_conditions DROP COLUMN rule_id_uuid;
ALTER TABLE building_rule_conditions ADD KEY idx_building_rule_conditions_rule_id (rule_id);

ALTER TABLE building_rule_values CHANGE COLUMN rule_id rule_id_uuid CHAR(36) NULL;
ALTER TABLE building_rule_values ADD COLUMN rule_id BIGINT UNSIGNED NULL;
UPDATE building_rule_values c JOIN building_rules p ON c.rule_id_uuid = p.public_id SET c.rule_id = p.id;
ALTER TABLE building_rule_values DROP COLUMN rule_id_uuid;
ALTER TABLE building_rule_values ADD KEY idx_building_rule_values_rule_id (rule_id);

ALTER TABLE building_rules CHANGE COLUMN rule_set_id rule_set_id_uuid CHAR(36) NULL;
ALTER TABLE building_rules ADD COLUMN rule_set_id BIGINT UNSIGNED NULL;
UPDATE building_rules c JOIN building_rule_sets p ON c.rule_set_id_uuid = p.public_id SET c.rule_set_id = p.id;
ALTER TABLE building_rules DROP COLUMN rule_set_id_uuid;
ALTER TABLE building_rules ADD KEY idx_building_rules_rule_set_id (rule_set_id);

ALTER TABLE drawing_approvals CHANGE COLUMN drawing_id drawing_id_uuid CHAR(36) NULL;
ALTER TABLE drawing_approvals ADD COLUMN drawing_id BIGINT UNSIGNED NULL;
UPDATE drawing_approvals c JOIN drawing_metadata p ON c.drawing_id_uuid = p.public_id SET c.drawing_id = p.id;
ALTER TABLE drawing_approvals DROP COLUMN drawing_id_uuid;
ALTER TABLE drawing_approvals ADD KEY idx_drawing_approvals_drawing_id (drawing_id);

ALTER TABLE drawing_approvals CHANGE COLUMN drawing_version_id drawing_version_id_uuid CHAR(36) NULL;
ALTER TABLE drawing_approvals ADD COLUMN drawing_version_id BIGINT UNSIGNED NULL;
UPDATE drawing_approvals c JOIN drawing_versions p ON c.drawing_version_id_uuid = p.public_id SET c.drawing_version_id = p.id;
ALTER TABLE drawing_approvals DROP COLUMN drawing_version_id_uuid;
ALTER TABLE drawing_approvals ADD KEY idx_drawing_approvals_drawing_version_id (drawing_version_id);

ALTER TABLE drawing_versions CHANGE COLUMN drawing_id drawing_id_uuid CHAR(36) NULL;
ALTER TABLE drawing_versions ADD COLUMN drawing_id BIGINT UNSIGNED NULL;
UPDATE drawing_versions c JOIN drawing_metadata p ON c.drawing_id_uuid = p.public_id SET c.drawing_id = p.id;
ALTER TABLE drawing_versions DROP COLUMN drawing_id_uuid;
ALTER TABLE drawing_versions ADD KEY idx_drawing_versions_drawing_id (drawing_id);

ALTER TABLE drawing_versions CHANGE COLUMN generation_job_id generation_job_id_uuid CHAR(36) NULL;
ALTER TABLE drawing_versions ADD COLUMN generation_job_id BIGINT UNSIGNED NULL;
UPDATE drawing_versions c JOIN generation_jobs p ON c.generation_job_id_uuid = p.public_id SET c.generation_job_id = p.id;
ALTER TABLE drawing_versions DROP COLUMN generation_job_id_uuid;
ALTER TABLE drawing_versions ADD KEY idx_drawing_versions_generation_job_id (generation_job_id);

ALTER TABLE estimate_items CHANGE COLUMN estimate_id estimate_id_uuid CHAR(36) NULL;
ALTER TABLE estimate_items ADD COLUMN estimate_id BIGINT UNSIGNED NULL;
UPDATE estimate_items c JOIN estimates p ON c.estimate_id_uuid = p.public_id SET c.estimate_id = p.id;
ALTER TABLE estimate_items DROP COLUMN estimate_id_uuid;
ALTER TABLE estimate_items ADD KEY idx_estimate_items_estimate_id (estimate_id);

ALTER TABLE estimate_items CHANGE COLUMN source_price_id source_price_id_uuid CHAR(36) NULL;
ALTER TABLE estimate_items ADD COLUMN source_price_id BIGINT UNSIGNED NULL;
UPDATE estimate_items c JOIN prices p ON c.source_price_id_uuid = p.public_id SET c.source_price_id = p.id;
ALTER TABLE estimate_items DROP COLUMN source_price_id_uuid;
ALTER TABLE estimate_items ADD KEY idx_estimate_items_source_price_id (source_price_id);

ALTER TABLE invoice_items CHANGE COLUMN invoice_id invoice_id_uuid CHAR(36) NULL;
ALTER TABLE invoice_items ADD COLUMN invoice_id BIGINT UNSIGNED NULL;
UPDATE invoice_items c JOIN invoices p ON c.invoice_id_uuid = p.public_id SET c.invoice_id = p.id;
ALTER TABLE invoice_items DROP COLUMN invoice_id_uuid;
ALTER TABLE invoice_items ADD KEY idx_invoice_items_invoice_id (invoice_id);

ALTER TABLE invoices CHANGE COLUMN milestone_id milestone_id_uuid CHAR(36) NULL;
ALTER TABLE invoices ADD COLUMN milestone_id BIGINT UNSIGNED NULL;
UPDATE invoices c JOIN milestones p ON c.milestone_id_uuid = p.public_id SET c.milestone_id = p.id;
ALTER TABLE invoices DROP COLUMN milestone_id_uuid;
ALTER TABLE invoices ADD KEY idx_invoices_milestone_id (milestone_id);

ALTER TABLE prices CHANGE COLUMN material_id material_id_uuid CHAR(36) NULL;
ALTER TABLE prices ADD COLUMN material_id BIGINT UNSIGNED NULL;
UPDATE prices c JOIN materials p ON c.material_id_uuid = p.public_id SET c.material_id = p.id;
ALTER TABLE prices DROP COLUMN material_id_uuid;
ALTER TABLE prices ADD KEY idx_prices_material_id (material_id);

ALTER TABLE prices CHANGE COLUMN service_id service_id_uuid CHAR(36) NULL;
ALTER TABLE prices ADD COLUMN service_id BIGINT UNSIGNED NULL;
UPDATE prices c JOIN services p ON c.service_id_uuid = p.public_id SET c.service_id = p.id;
ALTER TABLE prices DROP COLUMN service_id_uuid;
ALTER TABLE prices ADD KEY idx_prices_service_id (service_id);

ALTER TABLE rule_locations CHANGE COLUMN rule_set_id rule_set_id_uuid CHAR(36) NULL;
ALTER TABLE rule_locations ADD COLUMN rule_set_id BIGINT UNSIGNED NULL;
UPDATE rule_locations c JOIN building_rule_sets p ON c.rule_set_id_uuid = p.public_id SET c.rule_set_id = p.id;
ALTER TABLE rule_locations DROP COLUMN rule_set_id_uuid;
ALTER TABLE rule_locations ADD KEY idx_rule_locations_rule_set_id (rule_set_id);

ALTER TABLE rule_versions CHANGE COLUMN rule_set_id rule_set_id_uuid CHAR(36) NULL;
ALTER TABLE rule_versions ADD COLUMN rule_set_id BIGINT UNSIGNED NULL;
UPDATE rule_versions c JOIN building_rule_sets p ON c.rule_set_id_uuid = p.public_id SET c.rule_set_id = p.id;
ALTER TABLE rule_versions DROP COLUMN rule_set_id_uuid;
ALTER TABLE rule_versions ADD KEY idx_rule_versions_rule_set_id (rule_set_id);

