package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.MainInvoiceSettlementCreateRequest;
import pl.company.settlements.invoice.model.MainInvoiceSettlementUpdateRequest;
import pl.company.settlements.invoice.model.MainInvoiceWithSubcontractorInvoiceRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceResponse;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.MainInvoiceSettlementRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.task.domain.InvestmentTask;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MainInvoiceSettlementServiceTest {
    @Mock private MainInvoiceSettlementRepository repository;
    @Mock private MainContractorInvoiceRepository mainContractorInvoiceRepository;
    @Mock private SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    @Mock private SubcontractorInvoiceService subcontractorInvoiceService;
    @InjectMocks private MainInvoiceSettlementService service;

    @Test
    void createChecksCurrentTotalAfterLockingBothInvoices() {
        var main = mainInvoice();
        var sub = subInvoice(main.getMainContract());
        lock(main, sub);
        when(repository.sumBySubcontractorInvoiceId(40L)).thenReturn(n("60"), n("100"));
        when(repository.sumByMainContractorInvoiceId(10L)).thenReturn(n("40"));
        when(repository.save(any())).thenAnswer(call -> { MainInvoiceSettlement value = call.getArgument(0); value.setId(5L); return value; });

        var response = service.create(createRequest("40"));

        assertEquals(n("40"), response.getSettledAmount());
        assertEquals(n("0"), response.getRemainingToAssignFromSubcontractorInvoice());
        var order = inOrder(mainContractorInvoiceRepository, subcontractorInvoiceRepository, repository);
        order.verify(mainContractorInvoiceRepository).findByIdForUpdate(10L);
        order.verify(subcontractorInvoiceRepository).findByIdForUpdate(40L);
        order.verify(repository).sumBySubcontractorInvoiceId(40L);
        order.verify(repository).save(any());
    }

    @Test
    void staleAvailableAmountDoesNotAllowOverAssignment() {
        var main = mainInvoice(); lock(main, subInvoice(main.getMainContract()));
        when(repository.sumBySubcontractorInvoiceId(40L)).thenReturn(n("90"));
        assertThrows(IllegalArgumentException.class, () -> service.create(createRequest("20")));
        verify(repository, never()).save(any());
    }

    @Test
    void samePairIsRejectedWithoutDuplicatingAssignment() {
        var main = mainInvoice(); lock(main, subInvoice(main.getMainContract()));
        when(repository.existsByMainContractorInvoice_IdAndSubcontractorInvoice_Id(10L, 40L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(createRequest("10")));
        verify(repository, never()).save(any());
    }

    @Test
    void differentMainContractIsRejected() {
        var main = mainInvoice(); var other = mainInvoice().getMainContract(); other.setId(8L);
        lock(main, subInvoice(other));
        assertThrows(IllegalArgumentException.class, () -> service.create(createRequest("10")));
        verify(repository, never()).save(any());
    }

    @Test
    void differentCurrencyIsRejected() {
        var main = mainInvoice(); var sub = subInvoice(main.getMainContract()); sub.setCurrency("EUR"); lock(main, sub);
        assertThrows(IllegalArgumentException.class, () -> service.create(createRequest("10")));
        verify(repository, never()).save(any());
    }

    @Test
    void updateExcludesPreviousAmountAndKeepsBothInvoiceLinks() {
        var main = mainInvoice(); var sub = subInvoice(main.getMainContract()); lock(main, sub);
        var assignment = new MainInvoiceSettlement(main, sub); assignment.setId(5L); assignment.setSettledAmount(n("30"));
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(assignment));
        when(repository.sumBySubcontractorInvoiceIdExcluding(40L, 5L)).thenReturn(n("40"));
        when(repository.sumBySubcontractorInvoiceId(40L)).thenReturn(n("100"));
        when(repository.sumByMainContractorInvoiceId(10L)).thenReturn(n("60"));
        var request = new MainInvoiceSettlementUpdateRequest(); request.setSettledAmount(n("60"));

        var response = service.update(5L, request);

        assertEquals(n("60"), response.getSettledAmount());
        assertEquals(40L, response.getSubcontractorInvoiceId());
        assertEquals(10L, response.getMainContractorInvoiceId());
    }

    @Test
    void deleteRemovesOnlyAssignment() {
        var main = mainInvoice(); var sub = subInvoice(main.getMainContract()); lock(main, sub);
        var assignment = new MainInvoiceSettlement(main, sub); assignment.setId(5L);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(assignment));
        service.delete(5L);
        verify(repository).delete(assignment);
        verify(subcontractorInvoiceRepository, never()).delete(any(SubcontractorInvoice.class));
        verify(mainContractorInvoiceRepository, never()).delete(any(MainContractorInvoice.class));
    }

    @Test
    void combinedOperationPropagatesAssignmentFailureToTransaction() {
        var main = mainInvoice(); var sub = subInvoice(main.getMainContract()); lock(main, sub);
        when(repository.sumBySubcontractorInvoiceId(40L)).thenReturn(BigDecimal.ZERO);
        var invoiceRequest = new SubcontractorInvoiceCreateRequest();
        when(subcontractorInvoiceService.create(invoiceRequest)).thenReturn(SubcontractorInvoiceResponse.builder().id(40L).build());
        var request = new MainInvoiceWithSubcontractorInvoiceRequest();
        request.setMainContractorInvoiceId(10L); request.setInvoice(invoiceRequest); request.setSettledAmount(n("101"));
        assertThrows(IllegalArgumentException.class, () -> service.createWithInvoice(request));
        verify(repository, never()).save(any());
    }

    private void lock(MainContractorInvoice main, SubcontractorInvoice sub) {
        when(mainContractorInvoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(main));
        when(subcontractorInvoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(sub));
    }
    private MainContractorInvoice mainInvoice() {
        var contract = new MainContract(mock(InvestmentTask.class), new Contractor("GW", "1111111111")); contract.setId(7L);
        var invoice = new MainContractorInvoice(contract); invoice.setId(10L); invoice.setCurrency("PLN"); invoice.setGrossAmount(n("500"));
        return invoice;
    }
    private SubcontractorInvoice subInvoice(MainContract main) {
        var subcontract = new Subcontract(main, new Contractor("Podwykonawca", "2222222222")); subcontract.setId(21L);
        var invoice = new SubcontractorInvoice(subcontract); invoice.setId(40L); invoice.setCurrency("PLN"); invoice.setGrossAmount(n("100"));
        return invoice;
    }
    private MainInvoiceSettlementCreateRequest createRequest(String amount) {
        var request = new MainInvoiceSettlementCreateRequest(); request.setMainContractorInvoiceId(10L);
        request.setSubcontractorInvoiceId(40L); request.setSettledAmount(n(amount)); return request;
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
}
