package pl.company.settlements.contract.service;

import java.math.BigDecimal;

public final class ContractSettlementRules {
    private ContractSettlementRules() { }

    public static void validateIncrease(String description, BigDecimal limit, BigDecimal currentTotal,
                                        BigDecimal previousAmount, BigDecimal proposedAmount) {
        BigDecimal current = zero(currentTotal);
        BigDecimal proposedTotal = current.subtract(zero(previousAmount)).add(proposedAmount);
        // Pozwala stopniowo poprawiać błędne dane zapisane przed wprowadzeniem walidacji.
        if (proposedTotal.compareTo(limit) > 0 && proposedTotal.compareTo(current) > 0) {
            throw new IllegalArgumentException(description + " Po zapisie: " + proposedTotal.toPlainString()
                    + " PLN; dopuszczalna kwota: " + limit.toPlainString()
                    + " PLN; przekroczenie: " + proposedTotal.subtract(limit).toPlainString() + " PLN.");
        }
    }

    public static void validateValueReduction(BigDecimal currentValue, BigDecimal proposedValue,
                                               BigDecimal invoicedAmount, BigDecimal settledAmount) {
        BigDecimal minimum = zero(invoicedAmount).max(zero(settledAmount));
        if (proposedValue.compareTo(currentValue) < 0 && proposedValue.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("Nie można obniżyć wartości umowy poniżej sumy jej faktur lub rozliczeń."
                    + " Minimalna wartość brutto: " + minimum.toPlainString() + " PLN.");
        }
    }

    public static void validateInvoiceReduction(BigDecimal previousGross, BigDecimal proposedGross,
                                                 BigDecimal settledAmount) {
        if (proposedGross.compareTo(previousGross) < 0 && proposedGross.compareTo(zero(settledAmount)) < 0) {
            throw new IllegalArgumentException("Kwota brutto faktury nie może być mniejsza od sumy jej płatności i potrąceń."
                    + " Rozliczono: " + zero(settledAmount).toPlainString() + " PLN.");
        }
    }

    public static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
