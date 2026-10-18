package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.stream.Stream;

class SettlementReviewRulesTest {
    @TestFactory Stream<DynamicTest> validatesRetentionReviews() {
        return SettlementReviewCases.cases().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> c.check().run()));
    }
}
