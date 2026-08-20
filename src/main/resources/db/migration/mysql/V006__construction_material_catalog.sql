-- AVAS V006: the collected construction-material product catalogue.
--
-- Hibernate creates this table under ddl-auto=update, so this file exists for the same reason
-- V001 does: an environment that runs with ddl-auto=validate, or a DBA reviewing what the
-- application will write, needs the DDL stated rather than inferred from the entity.
--
-- Two representations are stored on purpose. The typed columns are the normalised product, which
-- is what the catalogue is searched by. raw_payload is the collector's verbatim reading of the
-- page, kept so a normalisation rule that turns out wrong can be replayed against stored data
-- instead of forcing a re-crawl of every source site.

CREATE TABLE IF NOT EXISTS catalog_material_products (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,

    -- Identity across runs: normally a hash of source_site + product_url, falling back to
    -- source_site + SKU where the site's URLs carry session or tracking parameters.
    fingerprint VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(60) NOT NULL,

    -- Provenance
    source_site VARCHAR(60) NOT NULL,
    product_url VARCHAR(1000) NOT NULL,
    source_product_id VARCHAR(120),
    collector_run_id VARCHAR(80),

    -- Identity
    name VARCHAR(300) NOT NULL,
    category VARCHAR(40) NOT NULL,
    subcategory VARCHAR(160),
    source_category_path VARCHAR(500),
    brand VARCHAR(160),
    manufacturer VARCHAR(200),
    model_code VARCHAR(120),
    sku VARCHAR(120),
    description TEXT,

    -- Physical description
    size VARCHAR(200),
    material_composition VARCHAR(200),
    specifications TEXT,
    attributes TEXT,

    -- Commercial terms. price is nullable because a great many Indian construction listings
    -- quote on enquiry, and a catalogue that only admitted priced rows would omit most of them.
    price DECIMAL(19,4),
    discount_price DECIMAL(19,4),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    unit VARCHAR(40),
    minimum_order_quantity DECIMAL(19,3),
    minimum_order_unit VARCHAR(40),
    stock_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',

    -- Seller
    seller_name VARCHAR(200),
    seller_location VARCHAR(200),
    seller_city VARCHAR(120),
    seller_state VARCHAR(120),

    -- Reputation and media
    rating DECIMAL(3,2),
    review_count INT,
    image_urls TEXT,

    -- Governance. Collected rows are Tier 3 market observation and are not published until an
    -- administrator accepts them, which is why the default is PENDING and not APPROVED.
    review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    review_note VARCHAR(1000),
    reviewed_by BINARY(16),
    reviewed_at TIMESTAMP(6) NULL,

    raw_payload TEXT,
    first_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    observation_count INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    UNIQUE KEY uk_material_product_public_id (public_id),
    UNIQUE KEY idx_material_product_fingerprint (fingerprint),
    KEY idx_material_product_search (review_status, category, source_site),
    KEY idx_material_product_brand (brand),
    KEY idx_material_product_run (collector_run_id)
);
