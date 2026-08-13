package com.avas.platform.auth;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleDefinitionTest {
    @Test
    void newerSystemDefinitionRepairsInactiveIndividualRoleAndRequiredPermissions() {
        var role = new RoleEntity("INDIVIDUAL", "Individual customer", Set.of("PROJECT_READ"), 5);
        role.configure(false, Set.of("PROJECT_READ"));

        role.upgradeDefinition(IdentityDefaults.displayName("INDIVIDUAL"),
                IdentityDefaults.REQUIRED_INDIVIDUAL_PERMISSIONS, IdentityDefaults.ROLE_DEFINITION_VERSION);

        assertThat(role.isActive()).isTrue();
        assertThat(role.getPermissions()).containsExactlyInAnyOrderElementsOf(
                IdentityDefaults.REQUIRED_INDIVIDUAL_PERMISSIONS);
    }
}
