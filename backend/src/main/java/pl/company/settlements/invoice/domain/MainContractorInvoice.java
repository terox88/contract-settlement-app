package pl.company.settlements.invoice.domain;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.file.domain.DocumentAttachment;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "main_contractor_invoice")
public class MainContractorInvoice extends Invoice {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "main_contract_id", nullable = false)
    private MainContract mainContract;

    @OneToMany(
            mappedBy = "mainContractorInvoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<DocumentAttachment> attachments = new ArrayList<>();

    @OneToMany(
            mappedBy = "mainContractorInvoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<MainInvoiceSettlement> settlements = new ArrayList<>();


}
