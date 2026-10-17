package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.Test;

class InvoiceAssignmentRulesTest {
    @Test
    void assignmentAndInvoiceEditsRespectAvailableAmountAndContext() {
        InvoiceAssignmentRulesCases.runAll();
    }
}
