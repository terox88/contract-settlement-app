package pl.company.settlements.contract.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ContractHierarchyRules {

    private ContractHierarchyRules() {
    }

    public record Change(Long id, LocalDate effectiveDate, BigDecimal valueChange, LocalDate newEndDate) {
    }

    public record State(BigDecimal value, LocalDate contractDate, LocalDate endDate,
                        Long contractorId, String taxId) {
    }

    public static State state(BigDecimal initialValue, LocalDate contractDate, LocalDate initialEndDate,
                              List<Change> amendments, Long contractorId, String taxId) {
        if (initialValue == null || initialValue.signum() < 0) {
            throw new IllegalArgumentException("Wartość początkowa umowy musi być nieujemna.");
        }
        if (contractDate == null) {
            throw new IllegalArgumentException("Data zawarcia umowy jest wymagana.");
        }
        List<Change> ordered = new ArrayList<>(amendments);
        ordered.sort(Comparator.comparing(Change::effectiveDate));
        BigDecimal value = initialValue;
        LocalDate endDate = initialEndDate;
        for (Change amendment : ordered) {
            if (amendment.valueChange() != null) value = value.add(amendment.valueChange());
            if (value.signum() < 0) {
                throw new IllegalArgumentException("Zmiana spowodowałaby ujemną wartość umowy od "
                        + amendment.effectiveDate() + ".");
            }
            if (amendment.newEndDate() != null) endDate = amendment.newEndDate();
        }
        return new State(value, contractDate, endDate, contractorId, taxId);
    }

    public static void validateSubcontract(State main, State subcontract) {
        validateContractor(main, subcontract.contractorId(), subcontract.taxId());
        if (subcontract.endDate() == null) {
            throw new IllegalArgumentException("Data zakończenia umowy podwykonawczej jest wymagana.");
        }
        if (subcontract.contractDate().isBefore(main.contractDate())) {
            throw new IllegalArgumentException("Data zawarcia umowy podwykonawczej nie może poprzedzać "
                    + "daty zawarcia umowy zleceniodawcy (" + main.contractDate() + ").");
        }
        if (subcontract.endDate().isBefore(subcontract.contractDate())) {
            throw new IllegalArgumentException("Data zakończenia umowy podwykonawczej nie może poprzedzać jej zawarcia.");
        }
    }

    public static BigDecimal subcontractsTotalValue(List<State> subcontracts) {
        return subcontracts.stream().map(State::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static void validateSubcontractsValue(State main, List<State> subcontracts) {
        BigDecimal total = subcontractsTotalValue(subcontracts);
        if (total.compareTo(main.value()) > 0) {
            throw new IllegalArgumentException("Suma aktualnych wartości brutto umów podwykonawczych ("
                    + total.toPlainString() + " PLN) nie może przekraczać aktualnej wartości umowy zleceniodawcy ("
                    + main.value().toPlainString() + " PLN). Przekroczenie: "
                    + total.subtract(main.value()).toPlainString() + " PLN.");
        }
    }

    public static boolean exceedsMainContractEndDate(LocalDate mainEndDate, LocalDate subcontractEndDate) {
        return mainEndDate != null && subcontractEndDate != null && subcontractEndDate.isAfter(mainEndDate);
    }

    public static void validateContractor(State main, Long subcontractorId, String subcontractorTaxId) {
        String mainTaxId = normalizeTaxId(main.taxId());
        String subTaxId = normalizeTaxId(subcontractorTaxId);
        if ((main.contractorId() != null && Objects.equals(main.contractorId(), subcontractorId))
                || (!mainTaxId.isEmpty() && mainTaxId.equals(subTaxId))) {
            throw new IllegalArgumentException("Wykonawca umowy zleceniodawcy nie może być jej podwykonawcą.");
        }
    }

    private static String normalizeTaxId(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
