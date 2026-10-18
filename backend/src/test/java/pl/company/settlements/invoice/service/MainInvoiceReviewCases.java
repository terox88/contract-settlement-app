package pl.company.settlements.invoice.service;

import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.file.domain.StoredFile;
import pl.company.settlements.file.service.ContractAttachmentService;
import pl.company.settlements.invoice.domain.Invoice;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.MainInvoiceReview;
import pl.company.settlements.invoice.repository.InvoiceReviewRepository;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bez bazy danych: rzeczywisty serwis, encje i reguły; odczyty repozytoriów zastępuje mała pamięć testowa. */
public final class MainInvoiceReviewCases {
    public record Case(String name, Runnable check) { }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static final class Fixture {
        final Contractor firm = new Contractor("Firma A", "1234563218");
        final MainContract contract = new MainContract(null, firm);
        final MainContractorInvoice main = new MainContractorInvoice(contract);
        final Subcontract subcontract = new Subcontract(contract, firm);
        final SubcontractorInvoice child = new SubcontractorInvoice(subcontract);
        final List<SubcontractorInvoice> subs = new ArrayList<>();
        final List<MainInvoiceSettlement> edges = new ArrayList<>();
        final List<PaymentAllocation> allocations = new ArrayList<>();
        final List<DocumentAttachment> files = new ArrayList<>();
        final MainInvoiceReviewService service;
        long nextFile = 100;
        Fixture() {
            firm.setId(2L); contract.setId(7L); contract.setContractNumber("UM/7"); subcontract.setId(21L); subcontract.setContractNumber("PW/21");
            fill(main,40L,"1000"); fill(child,41L,"500"); subs.add(child);
            MainInvoiceSettlement edge = new MainInvoiceSettlement(main,child); edge.setId(1L); edge.setSettledAmount(n("500")); edges.add(edge);
            Payment payment = new Payment(LocalDate.of(2026,10,18),n("500")); payment.setId(50L);
            PaymentAllocation allocation = new PaymentAllocation(payment,child); allocation.setId(60L); allocation.setAllocatedAmount(n("500"));
            allocation.setDeductedAmount(BigDecimal.ZERO); allocations.add(allocation); child.getPaymentAllocations().add(allocation);
            DocumentAttachment own = file(AttachmentType.MAIN_CONTRACTOR_INVOICE); own.setMainContractorInvoice(main);
            DocumentAttachment invoice = file(AttachmentType.SUBCONTRACTOR_INVOICE); invoice.setSubcontractorInvoice(child);
            DocumentAttachment statement = file(AttachmentType.SUBCONTRACTOR_STATEMENT); statement.setSubcontractorInvoice(child);
            DocumentAttachment proof = file(AttachmentType.PAYMENT_CONFIRMATION); proof.setPayment(payment);
            MainContractorInvoiceRepository invoices = (MainContractorInvoiceRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{MainContractorInvoiceRepository.class}, (proxy,method,args) -> switch(method.getName()) {
                        case "findById", "findByIdForUpdate" -> Optional.of(main);
                        case "findMainContractIdById" -> Optional.of(7L);
                        case "findAllByMainContract_IdOrderByIssueDateDescIdDesc" -> List.of(main);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            InvoiceReviewRepository data = (InvoiceReviewRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{InvoiceReviewRepository.class}, (proxy,method,args) -> switch(method.getName()) {
                        case "findSubInvoices" -> subs; case "findAssignments" -> edges; case "findAllocations" -> allocations;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            ContractAttachmentService documents = new ContractAttachmentService(null,null,null,null) {
                @Override public List<DocumentAttachment> entities(Long id) { return files; }
            };
            ContractHierarchyValidationService hierarchy = new ContractHierarchyValidationService(null,null,null) {
                @Override public MainContract lockMainContract(Long id) { return contract; }
            };
            service = new MainInvoiceReviewService(invoices,data,documents,hierarchy);
        }
        DocumentAttachment file(AttachmentType type) {
            StoredFile stored = new StoredFile("scan.pdf", "file-"+nextFile, "application/pdf", 10L, LocalDateTime.of(2026,10,18,10,0)); stored.setId(nextFile);
            DocumentAttachment attachment = new DocumentAttachment(stored,type); attachment.setId(nextFile++); files.add(attachment); return attachment;
        }
        MainInvoiceReview current() { return service.get(40L); }
        MainInvoiceReview close() { return service.complete(40L,current().fingerprint(),false,null,true); }
        SubcontractorInvoice grandchild() {
            Subcontract contract = new Subcontract(this.contract,firm); contract.setId(22L); contract.setParentSubcontract(subcontract);
            SubcontractorInvoice invoice = new SubcontractorInvoice(contract); fill(invoice,42L,"100"); subs.add(invoice);
            MainInvoiceSettlement edge = MainInvoiceSettlement.forSubcontractorParent(child,invoice); edge.setId(2L); edge.setSettledAmount(n("100")); edges.add(edge); return invoice;
        }
    }
    private static void fill(Invoice invoice,Long id,String gross) {
        invoice.setId(id); invoice.setInvoiceNumber("FV/"+id); invoice.setIssueDate(LocalDate.of(2026,10,18));
        invoice.setNetAmount(n(gross)); invoice.setGrossAmount(n(gross)); invoice.setVatAmount(BigDecimal.ZERO); invoice.setCurrency("PLN");
    }
    public static List<Case> cases() {
        return List.of(
            new Case("Potwierdzenie nie wymaga równości kwot GW i PW", () -> {var f=new Fixture();eq("COMPLETED",f.close().status());}),
            new Case("Lista zwraca status bez wchodzenia w szczegóły", () -> {var f=new Fixture();f.close();eq("COMPLETED",f.service.list(7L).get(0).status());}),
            new Case("Niekompletne dokumenty blokują potwierdzenie bez wyjątku", () -> {var f=new Fixture();f.files.remove(2);reject(f::close);}),
            new Case("Brakujące dokumenty mogą być niewymagane z uzasadnieniem", () -> {var f=new Fixture();f.files.clear();var r=f.service.complete(40L,f.current().fingerprint(),true,"Dokumenty nie są wymagane",true);eq("COMPLETED",r.status());}),
            new Case("Wyjątek bez uzasadnienia jest odrzucany", () -> {var f=new Fixture();reject(()->f.service.complete(40L,f.current().fingerprint(),true," ",true));}),
            new Case("Brak świadomego potwierdzenia jest odrzucany", () -> {var f=new Fixture();reject(()->f.service.complete(40L,f.current().fingerprint(),false,null,false));}),
            new Case("Brak dokumentu dalszego podwykonawcy jest widoczny", () -> {var f=new Fixture();f.grandchild();eq(2,f.current().linkedInvoiceCount());eq(true,f.current().missingDocuments().stream().anyMatch(x->x.contains("FV/42")));reject(f::close);}),
            new Case("Zmiana przypisania unieważnia potwierdzenie", () -> {var f=new Fixture();f.close();f.edges.get(0).setSettledAmount(n("400"));eq("REVIEW_REQUIRED",f.current().status());}),
            new Case("Zmiana kwoty płatności unieważnia potwierdzenie", () -> {var f=new Fixture();f.close();f.allocations.get(0).setAllocatedAmount(n("400"));eq("REVIEW_REQUIRED",f.current().status());}),
            new Case("Usunięcie pliku unieważnia potwierdzenie", () -> {var f=new Fixture();f.close();f.files.remove(2);eq("REVIEW_REQUIRED",f.current().status());}),
            new Case("Dodanie dokumentu do faktury wymaga ponownego sprawdzenia", () -> {var f=new Fixture();f.close();f.file(AttachmentType.ACCEPTANCE_PROTOCOL).setSubcontractorInvoice(f.child);eq("REVIEW_REQUIRED",f.current().status());}),
            new Case("Zmiana nazwy wyświetlanej nie zmienia treści zatwierdzonego rozliczenia", () -> {var f=new Fixture();f.close();f.files.get(0).setDisplayName("Nowa nazwa");eq("COMPLETED",f.current().status());}),
            new Case("Dokument umowy poza fakturą nie unieważnia potwierdzenia", () -> {var f=new Fixture();f.close();f.file(AttachmentType.CONTRACT).setContract(f.contract);eq("COMPLETED",f.current().status());}),
            new Case("Zmiana stawki VAT unieważnia potwierdzenie", () -> {var f=new Fixture();f.close();f.main.getVatRates().add("ZW");eq("REVIEW_REQUIRED",f.current().status());}),
            new Case("Zmiana NIP wykonawcy wymaga ponownej kontroli", () -> {var f=new Fixture();f.close();f.firm.setTaxId("1111111111");eq("REVIEW_REQUIRED",f.current().status());}),
            new Case("Zmiana danych faktury unieważnia potwierdzenie", () -> {var f=new Fixture();f.close();f.main.setInvoiceNumber("NOWY");eq("REVIEW_REQUIRED",f.current().status());}),
            new Case("Nieaktualny podgląd nie może zatwierdzić nowego stanu", () -> {var f=new Fixture();String hash=f.current().fingerprint();f.edges.get(0).setSettledAmount(n("400"));reject(()->f.service.complete(40L,hash,false,null,true));}),
            new Case("Ponowne sprawdzenie po zmianie przywraca status potwierdzony", () -> {var f=new Fixture();f.close();f.edges.get(0).setSettledAmount(n("400"));eq("COMPLETED",f.close().status());}),
            new Case("Cofnięcie potwierdzenia przywraca otwartą fakturę", () -> {var f=new Fixture();f.close();eq("OPEN",f.service.reopen(40L).status());}),
            new Case("Wspólny przelew jest kontrolowany raz", () -> {
                var f=new Fixture(); SubcontractorInvoice second=new SubcontractorInvoice(f.subcontract); fill(second,43L,"100"); f.subs.add(second);
                MainInvoiceSettlement edge=new MainInvoiceSettlement(f.main,second); edge.setId(3L); edge.setSettledAmount(n("100")); f.edges.add(edge);
                PaymentAllocation allocation=new PaymentAllocation(f.allocations.get(0).getPayment(),second); allocation.setId(61L); allocation.setAllocatedAmount(n("100")); allocation.setDeductedAmount(BigDecimal.ZERO); f.allocations.add(allocation);
                f.files.remove(3); eq(1L,f.current().missingDocuments().stream().filter(x->x.contains("Przelew nr")).count());
            }),
            new Case("Brak PW jest ostrzeżeniem, można świadomie potwierdzić fakturę", () -> {var f=new Fixture();f.edges.clear();eq(0,f.current().linkedInvoiceCount());eq(false,f.current().warnings().isEmpty());eq("COMPLETED",f.close().status());})
        );
    }
    private static void reject(Runnable action){try{action.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection");}
    private static void eq(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+" got "+actual);}
    public static void main(String[] args){for(var c:cases())try{c.check().run();}catch(Throwable e){throw new AssertionError(c.name(),e);}System.out.println("Main invoice review: "+cases().size()+" checks passed");}
}
