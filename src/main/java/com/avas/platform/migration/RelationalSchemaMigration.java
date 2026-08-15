package com.avas.platform.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Versioned MySQL baseline derived from the AVAS detailed build specification.
 * Hibernate continues to maintain mapped aggregates while this migration owns
 * cross-module tables that do not yet have application entities.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RelationalSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RelationalSchemaMigration.class);
    private static final String SPEC_SCHEMA = "db/migration/mysql/V001__specification_transactional_schema.sql";
    private static final String LONG_KEY_SCHEMA = "db/migration/mysql/V005__long_surrogate_keys.sql";
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    RelationalSchemaMigration(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) return;
        }
        ensureLedger();
        applyScript("V001__specification_transactional_schema",
                "AVAS specification transactional schema", SPEC_SCHEMA);
        applyAction("V002__optional_email_compatibility",
                "Allow username/mobile-only identities and orders", this::relaxOptionalEmails);
        applyAction("V003__governed_price_product_metadata",
                "Add brand, product code and specification to governed price evidence",
                this::addGovernedPriceProductMetadata);
        applyAction("V004__price_evidence_source_channel",
                "Record whether price evidence came from a user, an administrator or a collector",
                this::addPriceSourceChannel);
        applyScript("V005__long_surrogate_keys",
                "Move the specification schema to BIGINT surrogate keys", LONG_KEY_SCHEMA);
    }

    private void ensureLedger() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS database_schema_migrations (
                    version VARCHAR(100) PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    checksum CHAR(64) NOT NULL,
                    specification_version VARCHAR(80) NOT NULL,
                    executed_at TIMESTAMP(6) NOT NULL
                )
                """);
    }

    private void applyScript(String version, String description, String resourcePath) throws Exception {
        if (applied(version)) return;
        var resource = new ClassPathResource(resourcePath);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, StandardCharsets.UTF_8));
        }
        record(version, description, sha256(resource.getContentAsByteArray()));
        log.info("Applied relational migration {}", version);
    }

    private void applyAction(String version, String description, Runnable action) {
        if (applied(version)) return;
        action.run();
        record(version, description, sha256(description.getBytes(StandardCharsets.UTF_8)));
        log.info("Applied relational migration {}", version);
    }

    private boolean applied(String version) {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM database_schema_migrations WHERE version = ?",
                Integer.class, version);
        return count != null && count > 0;
    }

    private void record(String version, String description, String checksum) {
        jdbc.update("""
                INSERT INTO database_schema_migrations
                    (version, description, checksum, specification_version, executed_at)
                VALUES (?, ?, ?, ?, ?)
                """, version, description, checksum, "AVAS-SPEC-1.0-2026-08-01", Timestamp.from(Instant.now()));
    }

    private void relaxOptionalEmails() {
        relaxIfRequired("users", "email", "ALTER TABLE users MODIFY COLUMN email VARCHAR(190) NULL");
        relaxIfRequired("commerce_orders", "buyer_email",
                "ALTER TABLE commerce_orders MODIFY COLUMN buyer_email VARCHAR(190) NULL");
    }

    /**
     * Records the provenance channel on every price observation.
     *
     * <p>Existing rows predate automated collection, so they backfill to USER rather than to a
     * nullable unknown: leaving the column null would let untraceable evidence resolve rates with
     * the same weight as a submission someone is accountable for.</p>
     */
    private void addPriceSourceChannel() {
        addColumnIfMissing("price_submissions", "source_channel",
                "ALTER TABLE price_submissions ADD COLUMN source_channel VARCHAR(20) NOT NULL DEFAULT 'USER'");
        addColumnIfMissing("price_submissions", "source_url",
                "ALTER TABLE price_submissions ADD COLUMN source_url VARCHAR(1000) NULL");
        addColumnIfMissing("price_submissions", "collector_run_id",
                "ALTER TABLE price_submissions ADD COLUMN collector_run_id VARCHAR(80) NULL");
        addIndexIfMissing("price_submissions", "idx_price_submission_channel",
                "CREATE INDEX idx_price_submission_channel "
                        + "ON price_submissions (source_channel, status, city)");
    }

    /** MySQL has no CREATE INDEX IF NOT EXISTS, so existence is checked before creating. */
    private void addIndexIfMissing(String table, String index, String statement) {
        var present = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                """, Integer.class, table, index);
        if (present == null || present == 0) jdbc.execute(statement);
    }

    private void addGovernedPriceProductMetadata() {
        addColumnIfMissing("price_submissions", "brand_name",
                "ALTER TABLE price_submissions ADD COLUMN brand_name VARCHAR(120) NULL");
        addColumnIfMissing("price_submissions", "product_code",
                "ALTER TABLE price_submissions ADD COLUMN product_code VARCHAR(100) NULL");
        addColumnIfMissing("price_submissions", "specification",
                "ALTER TABLE price_submissions ADD COLUMN specification VARCHAR(1000) NULL");
    }

    private void relaxIfRequired(String table, String column, String statement) {
        var required = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ? AND is_nullable = 'NO'
                """, Integer.class, table, column);
        if (required != null && required > 0) jdbc.execute(statement);
    }

    private void addColumnIfMissing(String table, String column, String statement) {
        var present = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        if (present == null || present == 0) jdbc.execute(statement);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate migration checksum", exception);
        }
    }
}
