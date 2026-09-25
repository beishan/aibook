package com.aibook.controller;

import com.aibook.dto.backup.BackupExecutionView;
import com.aibook.dto.backup.BackupRetentionSettings;
import com.aibook.dto.backup.BackupTaskRequest;
import com.aibook.dto.backup.BackupTaskView;
import com.aibook.service.BackupService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/backups")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/path")
    public ResponseEntity<BackupService.BackupPathView> path() {
        return ResponseEntity.ok(backupService.path());
    }

    @GetMapping("/retention")
    public ResponseEntity<BackupRetentionSettings> retentionSettings() {
        return ResponseEntity.ok(backupService.retentionSettings());
    }

    @PutMapping("/retention")
    public ResponseEntity<BackupRetentionSettings> updateRetentionSettings(
            @RequestBody BackupRetentionSettings request) {
        return ResponseEntity.ok(backupService.updateRetentionSettings(request));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<BackupTaskView>> tasks() {
        return ResponseEntity.ok(backupService.tasks());
    }

    @PostMapping("/tasks")
    public ResponseEntity<BackupTaskView> createTask(@RequestBody BackupTaskRequest request) {
        return ResponseEntity.ok(backupService.createTask(request));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<BackupTaskView> updateTask(
            @PathVariable long id, @RequestBody BackupTaskRequest request) {
        return ResponseEntity.ok(backupService.updateTask(id, request));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable long id) {
        backupService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{id}/run")
    public ResponseEntity<BackupExecutionView> runTask(@PathVariable long id) {
        return ResponseEntity.accepted().body(backupService.runTask(id));
    }

    @PostMapping("/run")
    public ResponseEntity<BackupExecutionView> runImmediately(
            @RequestBody BackupTaskRequest request) {
        return ResponseEntity.accepted().body(backupService.runImmediately(request));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<BackupExecutionView>> executions() {
        return ResponseEntity.ok(backupService.executions());
    }
}
