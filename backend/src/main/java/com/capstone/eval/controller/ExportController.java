package com.capstone.eval.controller;

import com.capstone.eval.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/{taskId}/export")
    public ResponseEntity<byte[]> exportResults(@PathVariable Long taskId) {
        String csv = exportService.exportTaskResultsCsv(taskId);
        byte[] content = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=task_" + taskId + "_results.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }
}
