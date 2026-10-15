package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class InvoiceDocumentStateTest {
    @TestFactory
    Stream<DynamicTest> documentScenarios() {
        return InvoiceDocumentStateCases.scenarios().stream()
                .map(scenario -> DynamicTest.dynamicTest(scenario.name(), scenario.assertion()::run));
    }
}
