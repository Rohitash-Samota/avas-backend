package com.avas.platform.auth;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {
    private final AuthService auth;

    UserAdminController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping
    List<UserResponse> users() {
        return auth.users();
    }

    @PostMapping
    UserResponse createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return auth.createUser(request);
    }

    @PutMapping("/{userId}/roles")
    UserResponse assignRole(@PathVariable UUID userId, @Valid @RequestBody RoleAssignmentRequest request) {
        return auth.assignRole(userId, request.role());
    }

    @DeleteMapping("/{userId}/roles/{role}")
    UserResponse removeRole(@PathVariable UUID userId, @PathVariable String role) {
        return auth.removeRole(userId, role);
    }
}
