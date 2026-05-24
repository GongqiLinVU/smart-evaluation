package com.capstone.eval.config;

import com.capstone.eval.model.Rule;
import com.capstone.eval.model.RulePackage;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.repository.RulePackageRepository;
import com.capstone.eval.repository.RuleRepository;
import com.capstone.eval.service.RuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleDataSeeder implements ApplicationRunner {

    private final RuleService ruleService;
    private final RulePackageRepository rulePackageRepository;
    private final RuleRepository ruleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Seeding built-in rules...");
        ruleService.seedBuiltInRules();
        log.info("Built-in rules seeded.");

        seedDefaultRulePackage();
    }

    private void seedDefaultRulePackage() {
        if (rulePackageRepository.findByIsDefaultTrue().isPresent()) {
            log.info("Default rule package already exists, skipping seed.");
            return;
        }

        List<Rule> builtInRules = ruleRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getBuiltIn()))
                .toList();

        if (builtInRules.isEmpty()) {
            log.warn("No built-in rules found, cannot seed default rule package.");
            return;
        }

        RulePackage defaultPackage = RulePackage.builder()
                .name("Standard Evaluation")
                .description("Default rule package with all built-in criteria enabled (Logic, Methodology, Implementation)")
                .logicEnabled(true)
                .methodologyEnabled(true)
                .implementationEnabled(true)
                .logicWeight(1.0)
                .methodologyWeight(1.0)
                .implementationWeight(1.0)
                .isDefault(true)
                .build();

        RulePackage saved = rulePackageRepository.save(defaultPackage);

        for (Rule rule : builtInRules) {
            RulePackageItem item = RulePackageItem.builder()
                    .rulePackage(saved)
                    .rule(rule)
                    .enabled(true)
                    .weight(1.0)
                    .build();
            saved.getItems().add(item);
        }

        rulePackageRepository.save(saved);
        log.info("Default rule package '{}' seeded with {} rules.", saved.getName(), builtInRules.size());
    }
}
