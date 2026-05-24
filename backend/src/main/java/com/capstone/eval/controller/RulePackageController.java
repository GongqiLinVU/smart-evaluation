package com.capstone.eval.controller;

import com.capstone.eval.dto.RulePackageRequest;
import com.capstone.eval.dto.RulePackageResponse;
import com.capstone.eval.model.RulePackage;
import com.capstone.eval.service.RulePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rule-packages")
@RequiredArgsConstructor
public class RulePackageController {

    private final RulePackageService rulePackageService;

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<RulePackageResponse>> list() {
        List<RulePackageResponse> responses = rulePackageService.getAll().stream()
                .map(RulePackageResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<RulePackageResponse> get(@PathVariable Long id) {
        RulePackage rp = rulePackageService.getById(id);
        return ResponseEntity.ok(RulePackageResponse.fromEntity(rp));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<RulePackageResponse> create(@Valid @RequestBody RulePackageRequest request) {
        RulePackage rp = rulePackageService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RulePackageResponse.fromEntity(rp));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<RulePackageResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody RulePackageRequest request) {
        RulePackage rp = rulePackageService.update(id, request);
        return ResponseEntity.ok(RulePackageResponse.fromEntity(rp));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        rulePackageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
