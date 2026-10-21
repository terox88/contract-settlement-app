package pl.company.settlements.contract.model;

import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.SecurityValidityMode;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MainContractAdministrationResponse(
        LocalDate actualCompletionDate,
        Integer warrantyMonths,
        LocalDate ocInsuranceValidUntil,
        LocalDate carInsuranceValidUntil,
        PerformanceSecurityType securityType,
        BigDecimal securityAmount,
        SecurityValidityMode securityValidityMode,
        LocalDate securityValidUntil,
        BigDecimal retentionSecurityAmount,
        LocalDate retentionSecurityValidUntil,
        LocalDate warrantyEndDate,
        String securityTypeLabel,
        String securityValidityModeLabel,
        boolean securityReturned,
        boolean retentionSecurityReturned
) {}
