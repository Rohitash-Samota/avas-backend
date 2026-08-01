package com.avas.platform.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system")
public class SystemAdminController {
    private final DatabaseStatusService databases;

    SystemAdminController(DatabaseStatusService databases) { this.databases = databases; }

    @GetMapping("/database-status")
    DatabaseStatusService.DatabaseStatus databaseStatus() { return databases.status(); }
}
