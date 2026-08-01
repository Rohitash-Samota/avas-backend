package com.avas.platform.auth;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private Integer definitionVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 80)
    private Set<String> permissions = new LinkedHashSet<>();

    protected RoleEntity() {
    }

    public RoleEntity(String code, String displayName, Set<String> permissions, int definitionVersion) {
        this.code = code;
        this.displayName = displayName;
        this.active = true;
        this.definitionVersion = definitionVersion;
        this.permissions = new LinkedHashSet<>(permissions);
    }

    public void upgradeDefinition(String displayName, Set<String> permissions, int version) {
        if (definitionVersion != null && definitionVersion >= version) return;
        this.displayName = displayName;
        this.permissions = new LinkedHashSet<>(permissions);
        this.definitionVersion = version;
        this.active = true;
    }

    public void configure(boolean active, Set<String> permissions) {
        this.active = active;
        this.permissions = new LinkedHashSet<>(permissions);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof RoleEntity role && code != null && code.equalsIgnoreCase(role.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code == null ? null : code.toUpperCase());
    }
}
