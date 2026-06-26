package com.capstone.eval.service;

import com.capstone.eval.dto.RulePackageItemRequest;
import com.capstone.eval.dto.RulePackageRequest;
import com.capstone.eval.model.Rule;
import com.capstone.eval.model.RulePackage;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.repository.RulePackageRepository;
import com.capstone.eval.repository.RuleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RulePackageService {

    private final RulePackageRepository rulePackageRepository;
    private final RuleRepository ruleRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<RulePackage> getAll() {
        return rulePackageRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public RulePackage getById(Long id) {
        return rulePackageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule package not found: " + id));
    }

    public Optional<RulePackage> getDefault() {
        return rulePackageRepository.findByIsDefaultTrue();
    }

    @Transactional
    public RulePackage create(RulePackageRequest request) {
        validateWeights(request);
        RulePackage rp = RulePackage.builder()
                .name(request.name())
                .description(request.description())
                .logicEnabled(request.logicEnabled())
                .methodologyEnabled(request.methodologyEnabled())
                .implementationEnabled(request.implementationEnabled())
                .logicWeight(request.logicWeight())
                .methodologyWeight(request.methodologyWeight())
                .implementationWeight(request.implementationWeight())
                .scoringScale(request.scoringScale())
                .isDefault(Boolean.TRUE.equals(request.isDefault()))
                .build();

        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefaultFlag();
        }

        RulePackage saved = rulePackageRepository.save(rp);

        if (request.items() != null && !request.items().isEmpty()) {
            syncItems(saved, request.items());
            saved = rulePackageRepository.save(saved);
        }

        return saved;
    }

    @Transactional
    public RulePackage update(Long id, RulePackageRequest request) {
        validateWeights(request);
        RulePackage rp = getById(id);
        rp.setName(request.name());
        rp.setDescription(request.description());
        rp.setLogicEnabled(request.logicEnabled());
        rp.setMethodologyEnabled(request.methodologyEnabled());
        rp.setImplementationEnabled(request.implementationEnabled());
        rp.setLogicWeight(request.logicWeight());
        rp.setMethodologyWeight(request.methodologyWeight());
        rp.setImplementationWeight(request.implementationWeight());
        rp.setScoringScale(request.scoringScale());

        if (Boolean.TRUE.equals(request.isDefault()) && !Boolean.TRUE.equals(rp.getIsDefault())) {
            clearDefaultFlag();
            rp.setIsDefault(true);
        } else if (Boolean.FALSE.equals(request.isDefault())) {
            rp.setIsDefault(false);
        }

        if (request.items() != null) {
            syncItems(rp, request.items());
        }

        return rulePackageRepository.save(rp);
    }

    @Transactional
    public void delete(Long id) {
        rulePackageRepository.deleteById(id);
    }

    private void syncItems(RulePackage rp, List<RulePackageItemRequest> itemRequests) {
        rp.getItems().clear();
        entityManager.flush();
        for (RulePackageItemRequest itemReq : itemRequests) {
            Rule rule = ruleRepository.findById(itemReq.ruleId())
                    .orElseThrow(() -> new RuntimeException("Rule not found: " + itemReq.ruleId()));
            RulePackageItem item = RulePackageItem.builder()
                    .rulePackage(rp)
                    .rule(rule)
                    .enabled(itemReq.enabled())
                    .weight(itemReq.weight())
                    .maxPoints(itemReq.maxPoints())
                    .build();
            rp.getItems().add(item);
        }
    }

    private void clearDefaultFlag() {
        rulePackageRepository.findByIsDefaultTrue().ifPresent(existing -> {
            existing.setIsDefault(false);
            rulePackageRepository.save(existing);
        });
    }

    private void validateWeights(RulePackageRequest request) {
        double totalEnabled = 0;
        if (Boolean.TRUE.equals(request.logicEnabled())) totalEnabled += request.logicWeight();
        if (Boolean.TRUE.equals(request.methodologyEnabled())) totalEnabled += request.methodologyWeight();
        if (Boolean.TRUE.equals(request.implementationEnabled())) totalEnabled += request.implementationWeight();

        boolean hasItemRules = request.items() != null && !request.items().isEmpty();

        if (totalEnabled <= 0 && !hasItemRules) {
            throw new RuntimeException("At least one category must be enabled with a positive weight, or items must be provided");
        }
    }
}
