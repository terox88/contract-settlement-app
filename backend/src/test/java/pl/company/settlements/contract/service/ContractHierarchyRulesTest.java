package pl.company.settlements.contract.service;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class ContractHierarchyRulesTest {

    @TestFactory
    Stream<DynamicTest> validatesContractHierarchyRules() {
        return ContractHierarchyRuleCases.scenarios().stream()
                .map(scenario -> DynamicTest.dynamicTest(scenario.name(), scenario.assertion()::run));
    }
}
