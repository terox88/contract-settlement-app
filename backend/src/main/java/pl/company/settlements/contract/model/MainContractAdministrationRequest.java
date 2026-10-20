package pl.company.settlements.contract.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.SecurityValidityMode;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MainContractAdministrationRequest(
        LocalDate actualCompletionDate,
        @Min(0) @Max(1200) Integer warrantyMonths,
        LocalDate ocInsuranceValidUntil,
        LocalDate carInsuranceValidUntil,
        PerformanceSecurityType securityType,
        @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal securityAmount,
        SecurityValidityMode securityValidityMode,
        LocalDate securityValidUntil,
        @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal retentionSecurityAmount,
        LocalDate retentionSecurityValidUntil
) {}
