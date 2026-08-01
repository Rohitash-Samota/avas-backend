package com.avas.platform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.mongodb.repositories.type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.mongo.MongoHealthContributorAutoConfiguration",
        "management.health.mongo.enabled=false",
        "avas.mongo.enabled=false",
        "avas.identity.store=sql",
        "avas.bootstrap-admin.enabled=true",
        "avas.bootstrap-admin.username=platform.admin",
        "avas.bootstrap-admin.email=platform-admin@avas.test",
        "avas.bootstrap-admin.password=StrongAdmin@2026",
        "avas.bootstrap-admin.tenant-id=tenant-test"
})
@AutoConfigureMockMvc
class AuthApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void redirectsTheBackendRootToTheWebApplication() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000"));
    }

    @Test
    void publishesApiDiscoveryAndExplainsMissingAuthenticationAsJson() throws Exception {
        mvc.perform(get("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.architecture").value("MODULAR_MONOLITH"))
                .andExpect(jsonPath("$.databaseOwnership.mysql").isNotEmpty())
                .andExpect(jsonPath("$.databaseOwnership.mongodb").isNotEmpty());

        mvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/v1/projects"));
    }

    @Test
    void registersAuthenticatesAndRotatesARefreshSession() throws Exception {
        var email = "integration-" + UUID.randomUUID() + "@example.com";
        var registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "Avas", "lastName", "Customer", "email", email,
                                "mobileNumber", "+919876543210", "password", "secure-pass-2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.roles[0]").value("INDIVIDUAL"))
                .andReturn().getResponse();

        var body = json.readTree(registration.getContentAsString());
        var accessToken = body.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        var refreshCookie = registration.getCookie(AuthCookie.NAME);
        assertThat(refreshCookie).isNotNull();
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(AuthCookie.NAME, refreshCookie.getValue())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void removesPublicDemoIdentitiesAndAllowsControlledAdminUserCreation() throws Exception {
        mvc.perform(get("/api/v1/auth/demo-users"))
                .andExpect(status().isUnauthorized());

        var adminLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identifier", "platform.admin", "password", "StrongAdmin@2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
                .andReturn().getResponse();
        var token = json.readTree(adminLogin.getContentAsString()).path("accessToken").asText();
        var email = "architect-" + UUID.randomUUID() + "@example.com";

        mvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "New", "lastName", "Architect", "email", email,
                                "password", "secure-pass-2026", "role", "INTERNAL_USER",
                                "tenantId", "tenant-test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("INTERNAL_USER"));
    }

    @Test
    void publicRegistrationSupportsBuilderWithoutExposingPrivilegedRoles() throws Exception {
        var email = "builder-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "Avas", "lastName", "Builder", "email", email,
                                "password", "secure-pass-2026", "accountType", "BUILDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("BUILDER"))
                .andExpect(jsonPath("$.user.roles[1]").value("INDIVIDUAL"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "Avas", "lastName", "Admin", "email", "blocked-" + email,
                                "password", "secure-pass-2026", "accountType", "ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportsPhoneOnlyAccountsAndEveryLoginIdentifier() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var username = "phone." + suffix;
        var firstName = "Phone" + suffix;
        var mobile = "+91 91234 56789";

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", firstName, "lastName", "User", "username", username,
                                "mobileNumber", mobile, "password", "secure-pass-2026",
                                "accountType", "INDIVIDUAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.email").doesNotExist())
                .andExpect(jsonPath("$.user.mobileNumber").value("919123456789"));

        for (var identifier : List.of(username, "+91-91234-56789", firstName + " User")) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "identifier", identifier, "password", "secure-pass-2026"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.username").value(username));
        }
    }

    @Test
    void administratorCanProvisionEveryPrivilegedRole() throws Exception {
        var admin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identifier", "platform.admin", "password", "StrongAdmin@2026"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        var token = json.readTree(admin.getContentAsString()).path("accessToken").asText();
        for (var role : List.of("INTERNAL_USER", "SITE_ENGINEER", "ADMIN")) {
            var suffix = UUID.randomUUID().toString().substring(0, 8);
            var username = role.toLowerCase() + "." + suffix;
            mvc.perform(post("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Active-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "firstName", "Production", "lastName", "User", "username", username,
                                    "email", username + "@avas.test", "password", "StrongUser@2026", "role", role,
                                    "tenantId", "tenant-test"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles[0]").value(role));
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "identifier", username, "password", "StrongUser@2026"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.roles[0]").value(role));
        }
    }

    @Test
    void staleBrowserRoleCannotBlockSessionBootstrapAndActivePermissionsGateAdminApis() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var builderLogin = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "Role", "lastName", "Builder", "username", "role.builder." + suffix,
                                "email", "role-builder-" + suffix + "@avas.test", "password", "StrongUser@2026",
                                "accountType", "BUILDER"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        var builderToken = json.readTree(builderLogin.getContentAsString()).path("accessToken").asText();

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + builderToken)
                        .header("X-Active-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("BUILDER"))
                .andExpect(jsonPath("$.roleAccess[0].active").value(true));

        mvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + builderToken)
                        .header("X-Active-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTIVE_ROLE_NOT_ASSIGNED"));

        mvc.perform(get("/api/v1/workspace/summary")
                        .header("Authorization", "Bearer " + builderToken)
                        .header("X-Active-Role", "BUILDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BUILDER"))
                .andExpect(jsonPath("$.displayName").value("Builder"))
                .andExpect(jsonPath("$.permissions[?(@ == 'PRICE_SUBMIT')]").exists())
                .andExpect(jsonPath("$.workflow[0].permission").value("BUILDER_PROFILE_MANAGE"));

        var adminLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identifier", "platform.admin", "password", "StrongAdmin@2026"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        var adminBody = json.readTree(adminLogin.getContentAsString());
        var adminToken = adminBody.path("accessToken").asText();
        var adminId = adminBody.path("user").path("id").asText();

        mvc.perform(put("/api/v1/admin/users/{userId}/roles", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", "BUILDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(3))
                .andExpect(jsonPath("$.roles[1]").value("BUILDER"))
                .andExpect(jsonPath("$.roles[2]").value("INDIVIDUAL"));

        mvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Active-Role", "BUILDER"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Active-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].permissions.length()").isNumber());

        var ownerUsername = "tenant.owner." + suffix;
        mvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "Tenant", "lastName", "Owner", "username", ownerUsername,
                                "email", ownerUsername + "@avas.test", "password", "StrongUser@2026",
                                "role", "INDIVIDUAL", "tenantId", "tenant-test"))))
                .andExpect(status().isOk());
        var ownerLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identifier", ownerUsername, "password", "StrongUser@2026"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        var ownerToken = json.readTree(ownerLogin.getContentAsString()).path("accessToken").asText();
        var ownedProject = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("X-Active-Role", "INDIVIDUAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tenant-scoped project\",\"startMode\":\"PLOT\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var ownedProjectId = json.readTree(ownedProject.getContentAsString()).path("id").asText();

        mvc.perform(get("/api/v1/projects/{projectId}", ownedProjectId)
                        .header("Authorization", "Bearer " + adminToken).header("X-Active-Role", "ADMIN"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/{projectId}", ownedProjectId)
                        .header("Authorization", "Bearer " + adminToken).header("X-Active-Role", "BUILDER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectListAndReadsAreScopedToTheAuthenticatedOwner() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var first = registerAndToken("owner-" + suffix + "@example.com");
        var second = registerAndToken("other-" + suffix + "@example.com");

        var created = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + first)
                        .header("X-Active-Role", "INDIVIDUAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Persisted family home", "startMode", "PLOT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Persisted family home"))
                .andReturn().getResponse();
        var projectId = json.readTree(created.getContentAsString()).path("id").asText();

        mvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + first)
                        .header("X-Active-Role", "INDIVIDUAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + projectId + "')]").exists());

        mvc.perform(get("/api/v1/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + second)
                        .header("X-Active-Role", "INDIVIDUAL"))
                .andExpect(status().isNotFound());
    }

    private String registerAndToken(String email) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "Project", "lastName", "Owner", "email", email,
                                "password", "secure-pass-2026", "accountType", "INDIVIDUAL"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("accessToken").asText();
    }
}
