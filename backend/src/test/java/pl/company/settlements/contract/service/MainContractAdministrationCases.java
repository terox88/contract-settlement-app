package pl.company.settlements.contract.service;

import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.SecurityValidityMode;
import pl.company.settlements.contract.domain.SecurityReturnStage;
import pl.company.settlements.contract.model.MainContractAdministrationRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class MainContractAdministrationCases {
    private MainContractAdministrationCases() {}
    private static int checks;

    public static int run() {
        checks = 0;
        var empty = value(null, null, null, null, null, null, null, null);
        var response = MainContractAdministrationRules.toResponse(MainContractAdministrationRules.validatedValue(empty));
        yes(response.warrantyEndDate() == null && response.securityType() == null);
        yes(MainContractAdministrationRules.toResponse(null).securityTypeLabel() == null);
        yes(MainContractAdministrationRules.validatedValue(null) == null);
        yes(MainContractAdministrationRules.warrantyEndDate(date("2024-01-31"), 1).equals(date("2024-02-29")));
        yes(MainContractAdministrationRules.warrantyEndDate(date("2025-01-31"), 1).equals(date("2025-02-28")));
        yes(MainContractAdministrationRules.warrantyEndDate(date("2024-02-29"), 12).equals(date("2025-02-28")));
        yes(MainContractAdministrationRules.warrantyEndDate(date("2026-10-20"), 60).equals(date("2031-10-20")));
        yes(MainContractAdministrationRules.warrantyEndDate(date("2026-10-20"), 0).equals(date("2026-10-20")));
        yes(MainContractAdministrationRules.warrantyEndDate(null, 12) == null);
        yes(MainContractAdministrationRules.warrantyEndDate(date("2026-10-20"), null) == null);
        reject(() -> MainContractAdministrationRules.validatedValue(value("2026-10-20", -1, null, null, null, null, null, null)));
        reject(() -> MainContractAdministrationRules.validatedValue(value("2026-10-20", 1201, null, null, null, null, null, null)));
        reject(() -> MainContractAdministrationRules.validatedValue(value("9999-12-31", 1, null, null, null, null, null, null)));
        valid(value("2025-01-01", 60, null, null, null, null, null, null));
        valid(value("2028-01-01", 60, null, null, null, null, null, null));
        valid(value(null, null, PerformanceSecurityType.NOT_ESTABLISHED, null, null, null, null, null));
        valid(value(null, null, PerformanceSecurityType.CASH, null, null, null, "1000.01", null));
        reject(() -> valid(value(null, null, PerformanceSecurityType.CASH, SecurityValidityMode.SINGLE, "2026-12-31", null, null, null)));
        reject(() -> valid(value(null, null, PerformanceSecurityType.NOT_ESTABLISHED, null, null, null, "1000", null)));
        reject(() -> valid(value(null, null, null, null, "2026-12-31", null, null, null)));
        for (PerformanceSecurityType type : new PerformanceSecurityType[]{PerformanceSecurityType.BANK_GUARANTEE, PerformanceSecurityType.INSURANCE_GUARANTEE, PerformanceSecurityType.OTHER_DOCUMENT}) {
            valid(value(null, null, type, SecurityValidityMode.SINGLE, "2020-01-01", null, "1000", null));
            valid(value(null, null, type, SecurityValidityMode.TWO_STAGE, "2026-12-31", "2031-12-31", "1000", "300"));
            reject(() -> valid(value(null, null, type, null, "2026-12-31", null, null, null)));
            reject(() -> valid(value(null, null, type, SecurityValidityMode.SINGLE, null, null, null, null)));
            reject(() -> valid(value(null, null, type, SecurityValidityMode.SINGLE, "2026-12-31", "2031-12-31", null, null)));
            reject(() -> valid(value(null, null, type, SecurityValidityMode.TWO_STAGE, "2026-12-31", null, null, null)));
            reject(() -> valid(value(null, null, type, SecurityValidityMode.TWO_STAGE, "2026-12-31", "2026-12-30", null, null)));
            reject(() -> valid(value(null, null, type, SecurityValidityMode.TWO_STAGE, "2026-12-31", "2031-12-31", "1000", "1000.01")));
        }
        valid(value(null, null, PerformanceSecurityType.BANK_GUARANTEE, SecurityValidityMode.TWO_STAGE, "2026-12-31", "2026-12-31", "1000", "1000"));
        valid(value(null, null, PerformanceSecurityType.BANK_GUARANTEE, SecurityValidityMode.TWO_STAGE, "2026-12-31", "2031-12-31", null, null));
        for (String amount : new String[]{"-1", "0", "0.001", "10000000000000.00"}) {
            reject(() -> valid(value(null, null, PerformanceSecurityType.CASH, null, null, null, amount, null)));
        }
        valid(value(null, null, PerformanceSecurityType.CASH, null, null, null, "9999999999999.99", null));
        var full = MainContractAdministrationRules.toResponse(MainContractAdministrationRules.validatedValue(
                value("2026-10-20", 60, PerformanceSecurityType.BANK_GUARANTEE, SecurityValidityMode.TWO_STAGE,
                        "2027-01-01", "2032-01-01", "10000", "3000")));
        yes(full.warrantyEndDate().equals(date("2031-10-20")));
        yes(full.securityTypeLabel().equals("Gwarancja bankowa"));
        yes(full.securityValidityModeLabel().equals(SecurityValidityMode.TWO_STAGE.getLabel()));
        yes(full.retentionSecurityAmount().compareTo(new BigDecimal("3000")) == 0);
        yes(full.ocInsuranceValidUntil().equals(date("2020-01-01")));
        reject(() -> MainContractAdministrationRules.validateCompletionDate(date("2026-10-20"), date("2026-10-19")));
        MainContractAdministrationRules.validateCompletionDate(date("2026-10-20"), date("2026-10-20")); checks++;
        MainContractAdministrationRules.validateCompletionDate(date("2026-10-20"), date("2026-10-21")); checks++;
        MainContractAdministrationRules.validateCompletionDate(date("2026-10-20"), null); checks++;
        var cashTwo = value(null, null, PerformanceSecurityType.CASH, SecurityValidityMode.TWO_STAGE,
                null, null, "1000.01", "300.01");
        valid(cashTwo);
        reject(() -> valid(value(null, null, PerformanceSecurityType.CASH, null, null, null, "1000", "300")));
        reject(() -> valid(value(null, null, PerformanceSecurityType.CASH, SecurityValidityMode.TWO_STAGE, null, null, "1000", "1000.01")));
        var returned = MainContractAdministrationRules.validatedValue(cashTwo);
        MainContractAdministrationRules.setReturned(returned, SecurityReturnStage.PERFORMANCE, true);
        var marked = MainContractAdministrationRules.toResponse(returned);
        yes(marked.securityReturned() && !marked.retentionSecurityReturned());
        yes(marked.securityAmount().compareTo(new BigDecimal("1000.01")) == 0);
        MainContractAdministrationRules.setReturned(returned, SecurityReturnStage.RETENTION, true);
        yes(MainContractAdministrationRules.toResponse(returned).retentionSecurityReturned());
        var replacement = MainContractAdministrationRules.validatedValue(cashTwo);
        MainContractAdministrationRules.preserveReturns(returned, replacement);
        yes(MainContractAdministrationRules.toResponse(replacement).securityReturned());
        yes(MainContractAdministrationRules.toResponse(replacement).retentionSecurityReturned());
        reject(() -> MainContractAdministrationRules.preserveReturns(returned,
                MainContractAdministrationRules.validatedValue(value(null, null, PerformanceSecurityType.CASH, null, null, null, "1000", null))));
        reject(() -> MainContractAdministrationRules.setReturned(null, SecurityReturnStage.PERFORMANCE, true));
        reject(() -> MainContractAdministrationRules.setReturned(returned, null, true));
        reject(() -> MainContractAdministrationRules.setReturned(returned, SecurityReturnStage.PERFORMANCE, null));
        reject(() -> MainContractAdministrationRules.setReturned(MainContractAdministrationRules.validatedValue(empty), SecurityReturnStage.PERFORMANCE, true));
        var single = MainContractAdministrationRules.validatedValue(value(null, null, PerformanceSecurityType.BANK_GUARANTEE,
                SecurityValidityMode.SINGLE, "2026-12-31", null, "1000", null));
        reject(() -> MainContractAdministrationRules.setReturned(single, SecurityReturnStage.RETENTION, true));
        MainContractAdministrationRules.setReturned(single, SecurityReturnStage.PERFORMANCE, true);
        yes(single.getSecurityValidUntil().equals(date("2026-12-31")));
        MainContractAdministrationRules.setReturned(single, SecurityReturnStage.PERFORMANCE, false);
        yes(!MainContractAdministrationRules.toResponse(single).securityReturned());
        return checks;
    }
    private static MainContractAdministrationRequest value(String completed, Integer months, PerformanceSecurityType type,
            SecurityValidityMode mode, String until, String retentionUntil, String amount, String retentionAmount) {
        return new MainContractAdministrationRequest(date(completed), months, date("2020-01-01"), null,
                type, number(amount), mode, date(until), number(retentionAmount), date(retentionUntil));
    }
    private static LocalDate date(String value) { return value == null ? null : LocalDate.parse(value); }
    private static BigDecimal number(String value) { return value == null ? null : new BigDecimal(value); }
    private static void valid(MainContractAdministrationRequest value) { MainContractAdministrationRules.validatedValue(value); checks++; }
    private static void yes(boolean value) { if (!value) throw new AssertionError("Administration rule failed"); checks++; }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected rejected administration data");
    }
    public static void main(String[] args) { System.out.println("Contract administration cases passed: " + run()); }
}
