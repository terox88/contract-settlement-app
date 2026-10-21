package pl.company.settlements.contract.service;

import pl.company.settlements.contract.domain.MainContractAdministration;
import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.SecurityValidityMode;
import pl.company.settlements.contract.domain.SecurityReturnStage;
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
        boolean established = request.securityType() != null && request.securityType() != PerformanceSecurityType.NOT_ESTABLISHED;
        if (!established && (request.securityAmount() != null || request.securityValidityMode() != null
                || request.securityValidUntil() != null || request.retentionSecurityAmount() != null
                || request.retentionSecurityValidUntil() != null)) {
            throw new IllegalArgumentException("Dane zabezpieczenia wymagają wskazania jego ustanowionej formy.");
        }
        if (!document && (request.securityValidUntil() != null || request.retentionSecurityValidUntil() != null)) {
            throw new IllegalArgumentException("Terminy ważności dotyczą zabezpieczenia w formie dokumentu.");
        }
        if (request.securityValidityMode() != SecurityValidityMode.TWO_STAGE && request.retentionSecurityAmount() != null) {
            throw new IllegalArgumentException("Zredukowana kwota wymaga wariantu dwóch etapów.");
        }
        if (request.securityAmount() != null && request.retentionSecurityAmount() != null
                && request.retentionSecurityAmount().compareTo(request.securityAmount()) > 0) {
            throw new IllegalArgumentException("Zredukowana kwota nie może przekraczać pełnej kwoty zabezpieczenia.");
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

    public static void validateCompletionDate(LocalDate contractDate, LocalDate actualCompletionDate) {
        if (contractDate != null && actualCompletionDate != null && actualCompletionDate.isBefore(contractDate)) {
            throw new IllegalArgumentException("Faktyczna data zakończenia umowy nie może być wcześniejsza niż data jej zawarcia.");
        }
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

    public static void preserveReturns(MainContractAdministration previous, MainContractAdministration replacement) {
        if (previous == null || replacement == null) return;
        boolean returned = Boolean.TRUE.equals(previous.getSecurityReturned());
        boolean retentionReturned = Boolean.TRUE.equals(previous.getRetentionSecurityReturned());
        if ((returned || retentionReturned) && (previous.getSecurityType() != replacement.getSecurityType()
                || (previous.getSecurityValidityMode() == SecurityValidityMode.TWO_STAGE)
                != (replacement.getSecurityValidityMode() == SecurityValidityMode.TWO_STAGE))) {
            throw new IllegalArgumentException("Przed zmianą formy lub liczby etapów zabezpieczenia cofnij oznaczenie jego zwrotu w szczegółach umowy.");
        }
        replacement.setSecurityReturned(returned);
        replacement.setRetentionSecurityReturned(retentionReturned);
    }

    public static void setReturned(MainContractAdministration value, SecurityReturnStage stage, Boolean returned) {
        if (stage == null || returned == null) throw new IllegalArgumentException("Podaj etap i stan zwrotu zabezpieczenia.");
        if (value == null || value.getSecurityType() == null || value.getSecurityType() == PerformanceSecurityType.NOT_ESTABLISHED) {
            throw new IllegalArgumentException("Najpierw uzupełnij ustanowione zabezpieczenie umowy.");
        }
        if (stage == SecurityReturnStage.RETENTION) {
            if (value.getSecurityValidityMode() != SecurityValidityMode.TWO_STAGE) {
                throw new IllegalArgumentException("Zwrot części na gwarancję i rękojmię wymaga wariantu dwóch etapów.");
            }
            value.setRetentionSecurityReturned(returned);
        } else value.setSecurityReturned(returned);
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
                value.getSecurityValidityMode() == null ? null : value.getSecurityValidityMode().getLabel(),
                Boolean.TRUE.equals(value.getSecurityReturned()),
                Boolean.TRUE.equals(value.getRetentionSecurityReturned()));
    }
}
