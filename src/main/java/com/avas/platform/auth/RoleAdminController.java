package com.avas.platform.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import static com.avas.platform.auth.AuthModels.RoleResponse;
import static com.avas.platform.auth.AuthModels.RoleUpdateRequest;

@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleAdminController {
    private final AuthService auth;

    RoleAdminController(AuthService auth) { this.auth = auth; }

    @GetMapping
    List<RoleResponse> roles() { return auth.roleCatalog(); }

    @GetMapping("/permissions")
    Set<String> permissions() { return auth.permissionCatalog(); }

    @PutMapping("/{role}")
    RoleResponse configure(@PathVariable String role, @Valid @RequestBody RoleUpdateRequest request) {
        return auth.configureRole(role, request);
    }
}
