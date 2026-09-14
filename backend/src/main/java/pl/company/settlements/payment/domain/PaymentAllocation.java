package pl.company.settlements.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payment_allocation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_allocation",
                        columnNames = {"payment_id", "subcontractor_invoice_id"}
                )
        }
)
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcontractor_invoice_id", nullable = false)
    private SubcontractorInvoice subcontractorInvoice;

    @Column(name = "allocated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(
            name = "deducted_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal deductedAmount = BigDecimal.ZERO;
}


