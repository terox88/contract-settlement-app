package pl.company.settlements.payment.service;

import org.junit.jupiter.api.Test;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentPartiesTest {
    @Test void permitsSeveralInvoicesForTheSameParties() {
        assertDoesNotThrow(() -> PaymentParties.requireSame(new PaymentParties(3L, 2L, false), new PaymentParties(3L, 2L, false)));
    }
    @Test void rejectsOtherRecipientsAndOtherPayers() {
        assertThrows(IllegalArgumentException.class, () -> PaymentParties.requireSame(new PaymentParties(3L, 2L, false), new PaymentParties(4L, 2L, false)));
        assertThrows(IllegalArgumentException.class, () -> PaymentParties.requireSame(new PaymentParties(3L, 2L, false), new PaymentParties(3L, 1L, false)));
        assertThrows(IllegalArgumentException.class, () -> PaymentParties.requireSame(new PaymentParties(3L, null, true), new PaymentParties(3L, 2L, false)));
    }
    @Test void derivesThePayerFromTheDirectParentContract() {
        var mainFirm = firm(1L); var parentFirm = firm(2L); var childFirm = firm(3L);
        var root = new MainContract(null, mainFirm);
        var parent = new Subcontract(root, parentFirm);
        var child = new Subcontract(root, childFirm); child.setParentSubcontract(parent);
        assertEquals(new PaymentParties(3L, 2L, false), PaymentParties.of(new SubcontractorInvoice(child)));
        assertEquals(new PaymentParties(2L, 1L, false), PaymentParties.of(new SubcontractorInvoice(parent)));
        assertEquals(new PaymentParties(1L, null, true), PaymentParties.of(new MainContractorInvoice(root)));
    }
    private Contractor firm(Long id) { var result = new Contractor("Firma " + id, "000000000" + id); result.setId(id); return result; }
}
