package com.capstone.eval.config;

import com.capstone.eval.service.RuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleDataSeeder implements ApplicationRunner {

    private final RuleService ruleService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Seeding built-in rules...");
        ruleService.seedBuiltInRules();
        log.info("Built-in rules seeded.");
    }
}
