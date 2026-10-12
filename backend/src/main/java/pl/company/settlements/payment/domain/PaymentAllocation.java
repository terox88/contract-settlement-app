package pl.company.settlements.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment_allocation")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "payment_id",
            nullable = false
    )
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "subcontractor_invoice_id"
    )
    private SubcontractorInvoice subcontractorInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "main_contractor_invoice_id"
    )
    private MainContractorInvoice mainContractorInvoice;

    @Column(
            name = "allocated_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal allocatedAmount;

    @Column(
            name = "deducted_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal deductedAmount =
            BigDecimal.ZERO;

    public PaymentAllocation(
            Payment payment,
            SubcontractorInvoice subcontractorInvoice
    ) {
        this.payment = payment;
        this.subcontractorInvoice =
                subcontractorInvoice;
    }

    public PaymentAllocation(
            Payment payment,
            MainContractorInvoice mainContractorInvoice
    ) {
        this.payment = payment;
        this.mainContractorInvoice =
                mainContractorInvoice;
    }

    public boolean isSubcontractorInvoiceAllocation() {
        return subcontractorInvoice != null;
    }

    public boolean isMainContractorInvoiceAllocation() {
        return mainContractorInvoice != null;
    }
}