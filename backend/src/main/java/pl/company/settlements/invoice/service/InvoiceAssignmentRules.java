package pl.company.settlements.invoice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Reguły przypisania do faktury GW; przypisanie nie jest zapłatą. */
public final class InvoiceAssignmentRules {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    private InvoiceAssignmentRules() { }

    public static void validateAmount(BigDecimal gross, BigDecimal alreadyAssigned, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Kwota przypisania musi być dodatnia i mieścić się w dopuszczalnym zakresie.");
        }
        try {
            amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Kwota przypisania może mieć najwyżej dwa miejsca po przecinku.");
        }
        if (alreadyAssigned.add(amount).compareTo(gross) > 0) {
            throw new IllegalArgumentException("Suma przypisań przekracza wartość faktury podwykonawcy. Odśwież dane i sprawdź dostępną kwotę.");
        }
    }

    public static void validateContext(Long mainContractId, Long subcontractMainContractId,
                                       String mainCurrency, String subCurrency) {
        if (!Objects.equals(mainContractId, subcontractMainContractId)) {
            throw new IllegalArgumentException("Obie faktury muszą dotyczyć tej samej umowy głównej.");
        }
        if (mainCurrency == null || subCurrency == null || !mainCurrency.equalsIgnoreCase(subCurrency.trim())) {
            throw new IllegalArgumentException("Obie faktury muszą mieć tę samą walutę.");
        }
    }

    public static void validateInvoiceChange(BigDecimal newGross, BigDecimal assigned,
                                             String oldCurrency, String newCurrency) {
        if (newGross.compareTo(assigned) < 0) {
            throw new IllegalArgumentException("Wartość faktury nie może być mniejsza od kwoty przypisanej do faktur GW.");
        }
        if (assigned.signum() > 0 && !oldCurrency.equalsIgnoreCase(newCurrency.trim())) {
            throw new IllegalArgumentException("Najpierw usuń przypisania do faktur GW, aby zmienić walutę faktury.");
        }
    }
}
