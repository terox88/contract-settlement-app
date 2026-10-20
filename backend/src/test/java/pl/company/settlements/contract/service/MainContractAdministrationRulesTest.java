package pl.company.settlements.contract.service;

import org.junit.jupiter.api.Test;

class MainContractAdministrationRulesTest {
    @Test
    void validatesDatesSecurityFormsAndTwoStageRules() {
        MainContractAdministrationCases.run();
    }
}
