package com.avas.platform.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Applies additive columns needed by mapped pricing entities before Hibernate validates them.
 * The normal versioned migration runner records V003 after startup; this narrow bootstrap exists
 * so deployments using {@code JPA_DDL_AUTO=validate} can start against the previous AVAS schema.
 */
@Configuration(proxyBeanMethods = false)
class PreJpaPricingSchemaMigration {
    static final String BEAN_NAME = "preJpaGovernedPriceMetadataMigration";

    @Bean
    static BeanFactoryPostProcessor pricingMigrationBeforeEntityManagerFactory() {
        return beanFactory -> {
            if (!beanFactory.containsBeanDefinition("entityManagerFactory")) return;
            var definition = beanFactory.getBeanDefinition("entityManagerFactory");
            var dependencies = new LinkedHashSet<String>();
            if (definition.getDependsOn() != null) dependencies.addAll(Arrays.asList(definition.getDependsOn()));
            dependencies.add(BEAN_NAME);
            definition.setDependsOn(dependencies.toArray(String[]::new));
        };
    }

    @Bean(BEAN_NAME)
    PricingMetadataBootstrap pricingMetadataBootstrap(DataSource dataSource) {
        return new PricingMetadataBootstrap(dataSource);
    }

    static final class PricingMetadataBootstrap implements InitializingBean {
        private static final Logger log = LoggerFactory.getLogger(PricingMetadataBootstrap.class);
        private final DataSource dataSource;

        PricingMetadataBootstrap(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            try (var connection = dataSource.getConnection()) {
                if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) return;
            }
            var jdbc = new JdbcTemplate(dataSource);
            if (!tableExists(jdbc, "price_submissions")) return;
            addColumnIfMissing(jdbc, "brand_name",
                    "ALTER TABLE price_submissions ADD COLUMN brand_name VARCHAR(120) NULL");
            addColumnIfMissing(jdbc, "product_code",
                    "ALTER TABLE price_submissions ADD COLUMN product_code VARCHAR(100) NULL");
            addColumnIfMissing(jdbc, "specification",
                    "ALTER TABLE price_submissions ADD COLUMN specification VARCHAR(1000) NULL");
            log.info("Verified pre-JPA governed pricing metadata columns");
        }

        private boolean tableExists(JdbcTemplate jdbc, String table) {
            var present = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = DATABASE() AND table_name = ?
                    """, Integer.class, table);
            return present != null && present > 0;
        }

        private void addColumnIfMissing(JdbcTemplate jdbc, String column, String statement) {
            var present = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'price_submissions' AND column_name = ?
                    """, Integer.class, column);
            if (present == null || present == 0) jdbc.execute(statement);
        }
    }
}
