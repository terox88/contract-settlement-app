package pl.company.settlements.payment.service;

import org.junit.jupiter.api.Test;

class PaymentRulesTest {
    @Test
    void amountsRespectPrecisionCapacityAndInvoiceType() { PaymentRulesCases.runAll(); }
}
