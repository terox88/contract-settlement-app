package pl.company.settlements.contract.service;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class ContractSettlementRulesTest {
    @TestFactory
    Stream<DynamicTest> validatesContractSettlementLimits() {
        return ContractSettlementRuleCases.scenarios().stream()
                .map(scenario -> DynamicTest.dynamicTest(scenario.name(), scenario.assertion()::run));
    }
}
