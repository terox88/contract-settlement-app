package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.audit.service.BusinessAuditService;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.domain.DeductionRule;
import pl.company.settlements.contract.domain.FinancialLimit;
import pl.company.settlements.contract.domain.FinancialLimitYear;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.MainContractAdministration;
import pl.company.settlements.contract.domain.Subcontract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ContractAuditService {

    private final BusinessAuditService businessAuditService;

    public AuditSnapshot contractSnapshot(Contract contract) {
        AuditSnapshot snapshot = new AuditSnapshot()
                .field("contractNumber", "Numer umowy", contract.getContractNumber())
                .field("contractDate", "Data zawarcia", text(contract.getContractDate()))
                .field("initialValue", "Wartość początkowa brutto", decimal(contract.getInitialValue()))
                .field("vatRate", "Stawka VAT", decimal(contract.getVatRate()))
                .field("initialStartDate", "Data rozpoczęcia", text(contract.getInitialStartDate()))
                .field("initialEndDate", "Pierwotny termin zakończenia", text(contract.getInitialEndDate()))
                .field("contractType", "Rodzaj umowy",
                        contract.getContractType() == null ? null : contract.getContractType().getStatus())
                .field("remunerationType", "Rodzaj wynagrodzenia", text(contract.getRemunerationType()))
                .field("shortDescription", "Opis", contract.getShortDescription());

        if (contract instanceof MainContract main) {
            snapshot.field("investmentTaskId", "ID zadania", text(main.getInvestmentTask().getId()))
                    .field("mainContractorId", "ID wykonawcy", text(main.getMainContractor().getId()))
                    .field("mainContractorName", "Wykonawca", main.getMainContractor().getName());
            addAdministration(snapshot, main.getAdministration());
        } else if (contract instanceof Subcontract sub) {
            Contract parent = sub.getParentSubcontract() == null
                    ? sub.getMainContract() : sub.getParentSubcontract();
            snapshot.field("mainContractId", "ID umowy głównej", text(sub.getMainContract().getId()))
                    .field("parentContractId", "ID umowy nadrzędnej", text(parent.getId()))
                    .field("parentContractNumber", "Umowa nadrzędna", parent.getContractNumber())
                    .field("subcontractorId", "ID podwykonawcy", text(sub.getSubcontractor().getId()))
                    .field("subcontractorName", "Podwykonawca", sub.getSubcontractor().getName());
            addDeduction(snapshot, "performanceSecurityDeduction", "Zabezpieczenie",
                    sub.getPerformanceSecurityDeduction());
            addDeduction(snapshot, "otherDeduction", "Inne potrącenie", sub.getOtherDeduction());
        }

        return snapshot;
    }

    public AuditSnapshot amendmentSnapshot(ContractAmendment amendment) {
        return new AuditSnapshot()
                .field("contractId", "ID umowy", text(amendment.getContract().getId()))
                .field("amendmentNumber", "Numer aneksu", amendment.getAmendmentNumber())
                .field("amendmentDate", "Data aneksu", text(amendment.getAmendmentDate()))
                .field("effectiveDate", "Data obowiązywania", text(amendment.getEffectiveDate()))
                .field("valueChange", "Zmiana wartości brutto", decimal(amendment.getValueChange()))
                .field("newEndDate", "Nowy termin zakończenia", text(amendment.getNewEndDate()))
                .field("description", "Opis", amendment.getDescription());
    }

    public AuditSnapshot financialLimitSnapshot(FinancialLimit limit) {
        AuditSnapshot snapshot = new AuditSnapshot();
        limit.getYears().stream()
                .sorted(Comparator.comparingInt(FinancialLimitYear::getYear))
                .forEach(year -> snapshot.field(
                        "year_" + year.getYear(),
                        "Limit na " + year.getYear(),
                        decimal(year.getAmount())
                ));
        return snapshot;
    }

    public void recordContract(AuditOperation operation, Contract contract, AuditSnapshot before) {
        AuditEntityType entityType = contract instanceof MainContract
                ? AuditEntityType.MAIN_CONTRACT : AuditEntityType.SUBCONTRACT;
        record(operation, entityType, contract.getId(), contract.getContractNumber(),
                contract, before, contractSnapshot(contract));
    }

    public void recordAmendment(
            AuditOperation operation,
            Contract contract,
            ContractAmendment amendment,
            AuditSnapshot before
    ) {
        record(operation, AuditEntityType.CONTRACT_AMENDMENT, amendment.getId(),
                amendment.getAmendmentNumber(), contract, before, amendmentSnapshot(amendment));
    }

    public void recordFinancialLimit(
            AuditOperation operation,
            MainContract contract,
            FinancialLimit limit,
            AuditSnapshot before
    ) {
        record(operation, AuditEntityType.FINANCIAL_LIMIT, limit.getId(),
                "Limity umowy " + contract.getContractNumber(),
                contract, before, financialLimitSnapshot(limit));
    }

    private void record(
            AuditOperation operation,
            AuditEntityType entityType,
            Long entityId,
            String label,
            Contract contract,
            AuditSnapshot before,
            AuditSnapshot after
    ) {
        MainContract main;
        Long subcontractId = null;
        if (contract instanceof MainContract mainContract) {
            main = mainContract;
        } else if (contract instanceof Subcontract subcontract) {
            main = subcontract.getMainContract();
            subcontractId = subcontract.getId();
        } else {
            throw new IllegalArgumentException("Nieobsługiwany rodzaj umowy w historii zmian");
        }

        businessAuditService.record(operation, entityType, entityId, label,
                main.getInvestmentTask().getId(), main.getId(), subcontractId, before, after);
    }

    private void addAdministration(AuditSnapshot snapshot, MainContractAdministration administration) {
        if (administration == null) {
            return;
        }
        snapshot.field("administration.actualCompletionDate", "Faktyczne zakończenie",
                        text(administration.getActualCompletionDate()))
                .field("administration.warrantyMonths", "Gwarancja i rękojmia w miesiącach",
                        text(administration.getWarrantyMonths()))
                .field("administration.ocInsuranceValidUntil", "Ważność OC",
                        text(administration.getOcInsuranceValidUntil()))
                .field("administration.carInsuranceValidUntil", "Ważność CAR",
                        text(administration.getCarInsuranceValidUntil()))
                .field("administration.securityType", "Rodzaj zabezpieczenia",
                        administration.getSecurityType() == null ? null : administration.getSecurityType().getLabel())
                .field("administration.securityAmount", "Kwota zabezpieczenia",
                        decimal(administration.getSecurityAmount()))
                .field("administration.securityValidityMode", "Etapy zabezpieczenia",
                        administration.getSecurityValidityMode() == null
                                ? null : administration.getSecurityValidityMode().getLabel())
                .field("administration.securityValidUntil", "Ważność zabezpieczenia realizacji",
                        text(administration.getSecurityValidUntil()))
                .field("administration.retentionSecurityAmount", "Kwota zabezpieczenia gwarancji i rękojmi",
                        decimal(administration.getRetentionSecurityAmount()))
                .field("administration.retentionSecurityValidUntil", "Ważność zabezpieczenia gwarancji i rękojmi",
                        text(administration.getRetentionSecurityValidUntil()))
                .field("administration.securityReturned", "Zwrócono zabezpieczenie realizacji",
                        yesNo(administration.getSecurityReturned()))
                .field("administration.retentionSecurityReturned", "Zwrócono zabezpieczenie gwarancji i rękojmi",
                        yesNo(administration.getRetentionSecurityReturned()));
    }

    private void addDeduction(AuditSnapshot snapshot, String prefix, String label, DeductionRule rule) {
        if (rule != null) {
            snapshot.field(prefix + ".percentage", label + " — procent", decimal(rule.getPercentage()))
                    .field(prefix + ".basis", label + " — podstawa", text(rule.getBasis()));
        }
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private String yesNo(Boolean value) {
        return value == null ? null : value ? "Tak" : "Nie";
    }
}
