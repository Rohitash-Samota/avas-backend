package com.avas.platform.common;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

@Service
public class DatabaseStatusService {
    public record ConnectionStatus(String engine, boolean connected, String database, String endpoint,
            String authenticatedAs, int schemaObjects, String authentication, String message) {}
    public record DatabaseStatus(Instant checkedAt, ConnectionStatus mysql, ConnectionStatus mongodb) {
        public boolean isReady() { return mysql.connected() && mongodb.connected(); }
    }

    private final DataSource dataSource;
    private final ObjectProvider<MongoTemplate> mongoProvider;
    private final String mongoUri;

    DatabaseStatusService(DataSource dataSource, ObjectProvider<MongoTemplate> mongoProvider,
            @Value("${spring.data.mongodb.uri:}") String mongoUri) {
        this.dataSource = dataSource;
        this.mongoProvider = mongoProvider;
        this.mongoUri = mongoUri;
    }

    public DatabaseStatus status() {
        return new DatabaseStatus(Instant.now(), mysql(), mongo());
    }

    private ConnectionStatus mysql() {
        try (var connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            var database = connection.getCatalog();
            var objects = countTables(connection, database);
            return new ConnectionStatus("MySQL", connection.isValid(2), database,
                    safeJdbcEndpoint(metadata.getURL()), metadata.getUserName(), objects,
                    "CREDENTIALS_ACCEPTED", "Transactional system of record is reachable");
        } catch (Exception exception) {
            return new ConnectionStatus("MySQL", false, null, null, null, 0,
                    "CREDENTIALS_REJECTED_OR_UNREACHABLE", safeMessage(exception));
        }
    }

    private ConnectionStatus mongo() {
        var template = mongoProvider.getIfAvailable();
        if (template == null) {
            return new ConnectionStatus("MongoDB", false, null, safeMongoEndpoint(mongoUri), null, 0,
                    "DISABLED", "MongoDB integration is disabled");
        }
        try {
            var result = template.executeCommand("{ ping: 1 }");
            var connected = result.getDouble("ok") == 1.0;
            var database = template.getDb().getName();
            var collections = template.getCollectionNames().size();
            var mode = mongoUri.contains("@") ? "CREDENTIALS_ACCEPTED" : "NO_PASSWORD_LOCAL_ONLY";
            return new ConnectionStatus("MongoDB", connected, database, safeMongoEndpoint(mongoUri),
                    null, collections, mode, "Flexible document store is reachable");
        } catch (Exception exception) {
            return new ConnectionStatus("MongoDB", false, null, safeMongoEndpoint(mongoUri), null, 0,
                    "CREDENTIALS_REJECTED_OR_UNREACHABLE", safeMessage(exception));
        }
    }

    private static int countTables(Connection connection, String catalog) throws Exception {
        var count = 0;
        try (var tables = connection.getMetaData().getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) count++;
        }
        return count;
    }

    private static String safeJdbcEndpoint(String url) {
        if (url == null) return null;
        var query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }

    private static String safeMongoEndpoint(String uri) {
        if (uri == null || uri.isBlank()) return null;
        return uri.replaceFirst("mongodb(?:\\+srv)?://[^/@]+:[^/@]+@", "mongodb://***:***@");
    }

    private static String safeMessage(Exception exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.replaceAll("(?i)(password|pwd)=[^&\\s]+", "$1=***");
    }
}
