package com.capstone.eval.repository;

import com.capstone.eval.model.RulePackageItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RulePackageItemRepository extends JpaRepository<RulePackageItem, Long> {

    List<RulePackageItem> findByRulePackageId(Long rulePackageId);

    void deleteByRulePackageIdAndRuleId(Long rulePackageId, Long ruleId);
}
