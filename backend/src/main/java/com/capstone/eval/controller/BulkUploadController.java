package com.capstone.eval.controller;

import com.capstone.eval.dto.BulkUploadResponse;
import com.capstone.eval.service.BulkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
public class BulkUploadController {

    private final BulkUploadService bulkUploadService;

    @PostMapping("/{taskId}/bulk-upload")
    public ResponseEntity<BulkUploadResponse> bulkUpload(
            @PathVariable Long taskId,
            @RequestParam("files") List<MultipartFile> files) {
        BulkUploadResponse response = bulkUploadService.bulkUpload(taskId, files);
        return ResponseEntity.ok(response);
    }
}
