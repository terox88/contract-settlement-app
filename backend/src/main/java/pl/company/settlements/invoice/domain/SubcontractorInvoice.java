package pl.company.settlements.invoice.domain;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.payment.domain.PaymentAllocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "subcontractor_invoice")
public class SubcontractorInvoice extends Invoice {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcontract_id", nullable = false)
    private Subcontract subcontract;

    @OneToMany(
            mappedBy = "subcontractorInvoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<DocumentAttachment> attachments = new ArrayList<>();

    @OneToMany(
            mappedBy = "subcontractorInvoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PaymentAllocation> paymentAllocations = new ArrayList<>();

    public BigDecimal getPaidAmount() {
        return paymentAllocations.stream()
                .map(PaymentAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getRemainingAmount() {
        return grossAmount.subtract(getPaidAmount());
    }

    public boolean isPaid() {
        return getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0;
    }
}
