package pl.company.settlements.file.service;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class PdfPageSelectionTest {
    @TestFactory
    Stream<DynamicTest> validatesPdfPageSelectionAndOwnerTypes() {
        return PdfPageSelectionCases.scenarios().stream()
                .map(scenario -> DynamicTest.dynamicTest(scenario.name(), scenario.assertion()::run));
    }
}
