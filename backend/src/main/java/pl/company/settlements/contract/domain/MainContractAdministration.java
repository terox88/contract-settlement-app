package pl.company.settlements.contract.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Embeddable
public class MainContractAdministration {
    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "oc_insurance_valid_until")
    private LocalDate ocInsuranceValidUntil;

    @Column(name = "car_insurance_valid_until")
    private LocalDate carInsuranceValidUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "performance_security_type", length = 30)
    private PerformanceSecurityType securityType;

    @Column(name = "performance_security_amount", precision = 15, scale = 2)
    private BigDecimal securityAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_validity_mode", length = 30)
    private SecurityValidityMode securityValidityMode;

    @Column(name = "security_valid_until")
    private LocalDate securityValidUntil;

    @Column(name = "retention_security_amount", precision = 15, scale = 2)
    private BigDecimal retentionSecurityAmount;

    @Column(name = "retention_security_valid_until")
    private LocalDate retentionSecurityValidUntil;
}
