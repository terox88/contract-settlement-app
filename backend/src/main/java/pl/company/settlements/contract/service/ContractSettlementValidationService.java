package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.invoice.domain.Invoice;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ContractSettlementValidationService {
    private final MainContractorInvoiceRepository mainInvoiceRepository;
    private final SubcontractorInvoiceRepository subInvoiceRepository;
    private final PaymentAllocationRepository allocationRepository;

    // Wywoływane po zablokowaniu umowy głównej, przed zmianą kwot encji.
    public void validateInvoice(Contract contract, BigDecimal previousGross, BigDecimal proposedGross, String currency) {
        validateCurrency(currency);
        ContractSettlementRules.validateIncrease("Suma wartości brutto faktur przekracza aktualną wartość umowy "
                        + contract.getContractNumber() + ".", contract.getCurrentValue(), invoiced(contract), previousGross, proposedGross);
    }

    public void validateAllocation(Invoice invoice, BigDecimal previousPaid, BigDecimal previousDeducted,
                                    BigDecimal proposedPaid, BigDecimal proposedDeducted) {
        validateCurrency(invoice.getCurrency());
        BigDecimal proposed = proposedPaid.add(proposedDeducted);
        Contract contract = invoice instanceof MainContractorInvoice main ? main.getMainContract()
                : ((SubcontractorInvoice) invoice).getSubcontract();
        BigDecimal previousAmount = previousPaid.add(previousDeducted);
        Amounts contractAmounts = amounts(contract);
        Amounts invoiceAmounts = amounts(invoice);
        ContractSettlementRules.validateIncrease("Suma zapłat przekracza aktualną wartość umowy "
                        + contract.getContractNumber() + ".", contract.getCurrentValue(), contractAmounts.paid(), previousPaid, proposedPaid);
        ContractSettlementRules.validateIncrease("Suma zapłat i potrąceń przekracza aktualną wartość umowy "
                        + contract.getContractNumber() + ".", contract.getCurrentValue(), contractAmounts.total(), previousAmount, proposed);
        ContractSettlementRules.validateIncrease("Suma zapłat przekracza wartość brutto faktury "
                        + invoice.getInvoiceNumber() + ".", invoice.getGrossAmount(), invoiceAmounts.paid(), previousPaid, proposedPaid);
        ContractSettlementRules.validateIncrease("Suma zapłat i potrąceń przekracza wartość brutto faktury "
                        + invoice.getInvoiceNumber() + ".", invoice.getGrossAmount(), invoiceAmounts.total(), previousAmount, proposed);
    }

    public void validateContractValue(Contract contract, BigDecimal proposedValue) {
        if (proposedValue.compareTo(contract.getCurrentValue()) < 0) {
            ContractSettlementRules.validateValueReduction(contract.getCurrentValue(), proposedValue,
                    invoiced(contract), amounts(contract).total());
        }
    }

    public void validateInvoiceReduction(Invoice invoice, BigDecimal proposedGross) {
        if (proposedGross.compareTo(invoice.getGrossAmount()) < 0) {
            ContractSettlementRules.validateInvoiceReduction(invoice.getGrossAmount(), proposedGross, amounts(invoice).total());
        }
    }

    private BigDecimal invoiced(Contract contract) {
        return ContractSettlementRules.zero(contract instanceof MainContract
                ? mainInvoiceRepository.sumGrossAmountByMainContractId(contract.getId())
                : subInvoiceRepository.sumGrossAmountBySubcontractId(contract.getId()));
    }

    private record Amounts(BigDecimal paid, BigDecimal deducted) {
        BigDecimal total() { return paid.add(deducted); }
    }

    private Amounts amounts(Contract contract) {
        if (contract instanceof MainContract) {
            return new Amounts(ContractSettlementRules.zero(allocationRepository.sumPaidAmountByMainContractId(contract.getId())), BigDecimal.ZERO);
        }
        return new Amounts(ContractSettlementRules.zero(allocationRepository.sumPaidAmountBySubcontractId(contract.getId())),
                ContractSettlementRules.zero(allocationRepository.sumDeductedAmountBySubcontractId(contract.getId())));
    }

    private Amounts amounts(Invoice invoice) {
        if (invoice instanceof MainContractorInvoice) {
            return new Amounts(ContractSettlementRules.zero(allocationRepository.sumPaidAmountByMainContractorInvoiceId(invoice.getId())), BigDecimal.ZERO);
        }
        return new Amounts(ContractSettlementRules.zero(allocationRepository.sumPaidAmountBySubcontractorInvoiceId(invoice.getId())),
                ContractSettlementRules.zero(allocationRepository.sumDeductedAmountBySubcontractorInvoiceId(invoice.getId())));
    }

    private void validateCurrency(String currency) {
        if (currency == null || !"PLN".equalsIgnoreCase(currency.trim())) {
            throw new IllegalArgumentException("Wartość umowy jest określona w PLN. Faktura musi być w PLN,"
                    + " aby można było sprawdzić limit umowy; aplikacja nie przelicza kursów walut.");
        }
    }
}
