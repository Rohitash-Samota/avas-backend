package com.avas.platform.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Shared relational identity for SQL-backed entities.
 *
 * <p>Domain-facing identifiers remain explicit fields on concrete entities.
 * The generated database key is intentionally not exposed through the API.</p>
 */
@MappedSuperclass
public abstract class AbstractLongIdEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long databaseId;

    public Long getDatabaseId() {
        return databaseId;
    }
}
