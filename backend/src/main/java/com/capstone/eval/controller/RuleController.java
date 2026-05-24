package com.capstone.eval.controller;

import com.capstone.eval.dto.RuleRequest;
import com.capstone.eval.dto.RuleResponse;
import com.capstone.eval.model.Rule;
import com.capstone.eval.service.RuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @GetMapping
    public ResponseEntity<List<RuleResponse>> list() {
        List<RuleResponse> responses = ruleService.getAll().stream()
                .map(RuleResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RuleResponse> get(@PathVariable Long id) {
        Rule rule = ruleService.getById(id);
        return ResponseEntity.ok(RuleResponse.fromEntity(rule));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<RuleResponse> create(@Valid @RequestBody RuleRequest request) {
        Rule rule = ruleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RuleResponse.fromEntity(rule));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<RuleResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody RuleRequest request) {
        Rule rule = ruleService.update(id, request);
        return ResponseEntity.ok(RuleResponse.fromEntity(rule));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
