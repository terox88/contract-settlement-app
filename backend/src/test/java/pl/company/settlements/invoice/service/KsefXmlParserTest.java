package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class KsefXmlParserTest {
    @TestFactory
    Stream<DynamicTest> parsesSupportedInvoicesAndRejectsUnsafeOrAmbiguousInput() {
        return KsefXmlParserCases.cases().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> c.check().run()));
    }
}
