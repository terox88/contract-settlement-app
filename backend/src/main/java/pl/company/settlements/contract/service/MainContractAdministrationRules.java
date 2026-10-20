package pl.company.settlements.contract.service;

import pl.company.settlements.contract.domain.MainContractAdministration;
import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.SecurityValidityMode;
import pl.company.settlements.contract.model.MainContractAdministrationRequest;
import pl.company.settlements.contract.model.MainContractAdministrationResponse;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;

public final class MainContractAdministrationRules {
    private MainContractAdministrationRules() {}

    public static MainContractAdministration validatedValue(MainContractAdministrationRequest request) {
        if (request == null) return null;
        if (request.warrantyMonths() != null && (request.warrantyMonths() < 0 || request.warrantyMonths() > 1200)) {
            throw new IllegalArgumentException("Okres gwarancji i rękojmi musi wynosić od 0 do 1200 miesięcy.");
        }
        for (LocalDate date : new LocalDate[]{request.actualCompletionDate(), request.ocInsuranceValidUntil(),
                request.carInsuranceValidUntil(), request.securityValidUntil(), request.retentionSecurityValidUntil()}) {
            if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
                throw new IllegalArgumentException("Rok daty musi zawierać się w przedziale od 1 do 9999.");
            }
        }
        warrantyEndDate(request.actualCompletionDate(), request.warrantyMonths());
        validateAmount(request.securityAmount());
        validateAmount(request.retentionSecurityAmount());
        boolean document = request.securityType() != null && request.securityType().isDocument();
        if (!document && (request.securityValidityMode() != null || request.securityValidUntil() != null
                || request.retentionSecurityValidUntil() != null || request.retentionSecurityAmount() != null)) {
            throw new IllegalArgumentException("Terminy ważności i drugi etap dotyczą zabezpieczenia w formie dokumentu.");
        }
        if ((request.securityType() == null || request.securityType() == PerformanceSecurityType.NOT_ESTABLISHED)
                && request.securityAmount() != null) {
            throw new IllegalArgumentException("Kwota wymaga wskazania ustanowionego zabezpieczenia.");
        }
        if (document) {
            if (request.securityValidityMode() == null || request.securityValidUntil() == null) {
                throw new IllegalArgumentException("Dla dokumentu zabezpieczenia podaj wariant i termin ważności.");
            }
            if (request.securityValidityMode() == SecurityValidityMode.SINGLE
                    && (request.retentionSecurityAmount() != null || request.retentionSecurityValidUntil() != null)) {
                throw new IllegalArgumentException("Drugi termin i zredukowana kwota wymagają wariantu dwóch etapów.");
            }
            if (request.securityValidityMode() == SecurityValidityMode.TWO_STAGE) {
                if (request.retentionSecurityValidUntil() == null
                        || request.retentionSecurityValidUntil().isBefore(request.securityValidUntil())) {
                    throw new IllegalArgumentException("Podaj drugi termin ważności, nie wcześniejszy od pierwszego.");
                }
                if (request.securityAmount() != null && request.retentionSecurityAmount() != null
                        && request.retentionSecurityAmount().compareTo(request.securityAmount()) > 0) {
                    throw new IllegalArgumentException("Zredukowana kwota nie może przekraczać pełnej kwoty zabezpieczenia.");
                }
            }
        }
        MainContractAdministration value = new MainContractAdministration();
        value.setActualCompletionDate(request.actualCompletionDate());
        value.setWarrantyMonths(request.warrantyMonths());
        value.setOcInsuranceValidUntil(request.ocInsuranceValidUntil());
        value.setCarInsuranceValidUntil(request.carInsuranceValidUntil());
        value.setSecurityType(request.securityType());
        value.setSecurityAmount(request.securityAmount());
        value.setSecurityValidityMode(request.securityValidityMode());
        value.setSecurityValidUntil(request.securityValidUntil());
        value.setRetentionSecurityAmount(request.retentionSecurityAmount());
        value.setRetentionSecurityValidUntil(request.retentionSecurityValidUntil());
        return value;
    }

    public static LocalDate warrantyEndDate(LocalDate completed, Integer months) {
        if (completed == null || months == null) return null;
        try {
            LocalDate end = completed.plusMonths(months);
            if (end.getYear() > 9999) throw new DateTimeException("Out of range");
            return end;
        } catch (DateTimeException failure) {
            throw new IllegalArgumentException("Data końca gwarancji i rękojmi wykracza poza obsługiwany zakres.");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount != null && (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(new BigDecimal("9999999999999.99")) > 0)) {
            throw new IllegalArgumentException("Kwota zabezpieczenia musi być dodatnia, mieć najwyżej dwa miejsca po przecinku i nie przekraczać 9 999 999 999 999,99 zł.");
        }
    }

    public static MainContractAdministrationResponse toResponse(MainContractAdministration administration) {
        MainContractAdministration value = administration == null ? new MainContractAdministration() : administration;
        return new MainContractAdministrationResponse(
                value.getActualCompletionDate(),
                value.getWarrantyMonths(),
                value.getOcInsuranceValidUntil(),
                value.getCarInsuranceValidUntil(),
                value.getSecurityType(),
                value.getSecurityAmount(),
                value.getSecurityValidityMode(),
                value.getSecurityValidUntil(),
                value.getRetentionSecurityAmount(),
                value.getRetentionSecurityValidUntil(),
                warrantyEndDate(value.getActualCompletionDate(), value.getWarrantyMonths()),
                value.getSecurityType() == null ? null : value.getSecurityType().getLabel(),
                value.getSecurityValidityMode() == null ? null : value.getSecurityValidityMode().getLabel());
    }
}
