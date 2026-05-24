package com.capstone.eval.controller;

import com.capstone.eval.dto.LlmConfigRequest;
import com.capstone.eval.dto.LlmConfigResponse;
import com.capstone.eval.dto.PromptPreviewResponse;
import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.service.LlmConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llm-config")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigService llmConfigService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<List<LlmConfigResponse>> list() {
        List<LlmConfigResponse> responses = llmConfigService.getAll().stream()
                .map(LlmConfigResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<LlmConfigResponse> get(@PathVariable Long id) {
        LlmConfig config = llmConfigService.getById(id);
        return ResponseEntity.ok(LlmConfigResponse.fromEntity(config));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<LlmConfigResponse> create(@Valid @RequestBody LlmConfigRequest request) {
        LlmConfig config = llmConfigService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(LlmConfigResponse.fromEntity(config));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<LlmConfigResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody LlmConfigRequest request) {
        LlmConfig config = llmConfigService.update(id, request);
        return ResponseEntity.ok(LlmConfigResponse.fromEntity(config));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        llmConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<LlmConfigResponse> duplicate(@PathVariable Long id) {
        LlmConfig copy = llmConfigService.duplicate(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(LlmConfigResponse.fromEntity(copy));
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<PromptPreviewResponse> preview(@PathVariable Long id,
                                                          @RequestParam Long rulePackageId) {
        PromptPreviewResponse preview = llmConfigService.previewPrompt(id, rulePackageId);
        return ResponseEntity.ok(preview);
    }
}
