import test from 'node:test';
import assert from 'node:assert/strict';
import { completeInvoiceRequest, projectedDeduction, readDeductionReview, readMainInvoiceReview, readMainInvoiceReviews } from '../src/utils/reviews.js';
import { attachmentDownloadName, attachmentTitle, filterContractAttachments, renameAttachmentRequest } from '../src/utils/attachmentNames.js';
import { penaltyRequest, readPenalties, validatePenalty } from '../src/utils/penalties.js';
import { buildAllocationRequest } from '../src/utils/paymentForm.js';

const hash = 'a'.repeat(64);
const review = { invoiceId: 40, status: 'OPEN', label: 'Do rozliczenia', fingerprint: hash, linkedInvoiceCount: 0, missingDocuments: [], warnings: [] };
const retention = { status: 'MISMATCH', label: 'Rozbieżność', fingerprint: hash, expectedAmount: '50.00', actualAmount: '20.00', difference: '-30.00', securityAmount: '50.00', otherAmount: '0.00' };
const file = { id: 1, metadataVersion: 2, originalFilename: 'scan.pdf', displayName: 'Oświadczenie firmy A', type: 'SUBCONTRACTOR_STATEMENT' };
test('Rozliczenie GW wymaga jawnego potwierdzenia, bez porównania kwot GW i PW', () => {
    const result = completeInvoiceRequest(review, { checked: true, waived: false });
    assert.deepEqual(result, { fingerprint: hash, checked: true, documentsNotRequired: false, reason: null });
    assert.throws(() => completeInvoiceRequest(review, { checked: false, waived: false }));
});
test('Brak dokumentów wymaga uzupełnienia lub uzasadnionego wyjątku', () => {
    const value = { ...review, missingDocuments: ['Oświadczenie PW'] };
    assert.throws(() => completeInvoiceRequest(value, { checked: true, waived: false }));
    assert.throws(() => completeInvoiceRequest(value, { checked: true, waived: true, reason: ' ' }));
    assert.equal(completeInvoiceRequest(value, { checked: true, waived: true, reason: ' Nie jest wymagane dla tego rozliczenia. ' }).reason, 'Nie jest wymagane dla tego rozliczenia.');
});
test('Status po zmianach jest odrębny od potwierdzonego rozliczenia', () => {
    for (const status of ['OPEN','COMPLETED','REVIEW_REQUIRED']) assert.equal(readMainInvoiceReview({ ...review, status }, 40).status, status);
    assert.throws(() => readMainInvoiceReview({ ...review, status: 'PAID' }, 40));
});
test('Lista statusów musi pasować dokładnie do listy faktur', () => {
    assert.equal(readMainInvoiceReviews([review], [{id:40}]).size,1);
    assert.throws(() => readMainInvoiceReviews([], [{id:40}]));
    assert.throws(() => readMainInvoiceReviews([review, review], [{id:40},{id:41}]));
    assert.throws(() => readMainInvoiceReviews([{...review,invoiceId:42}], [{id:40}]));
});
test('Błędne podsumowanie nie może zostać potwierdzone', () => {
    for (const change of [{fingerprint:'bad'},{missingDocuments:null},{linkedInvoiceCount:-1},{warnings:[null]}]) assert.throws(() => readMainInvoiceReview({...review,...change},40));
});
test('Projektowane potrącenie dodaje nowy składnik do istniejącej sumy', () => {
    assert.deepEqual(projectedDeduction(retention,'30'),{actual:5000n,expected:5000n,difference:0n});
    assert.deepEqual(projectedDeduction(retention,'5'),{actual:2500n,expected:5000n,difference:-2500n});
});
test('Edycja rozliczenia zastępuje starą kwotę potrącenia, bez podwójnego liczenia', () => {
    assert.deepEqual(projectedDeduction(retention,'40','10'),{actual:5000n,expected:5000n,difference:0n});
});
test('Kontrola nie nalicza całych 5% od nowa dla kolejnego przelewu', () => {
    const after = projectedDeduction({...retention,actualAmount:'50.00'},'0');
    assert.equal(after.difference,0n);
});
test('Rozbieżność nie blokuje zbudowania żądania płatności', () => {
    const target={type:'sub',id:41};
    assert.notEqual(projectedDeduction(retention,'1').difference,0n);
    assert.equal(buildAllocationRequest(10,target,'100','1','100').data.deductedAmount,'1.00');
});
test('Nieprawidłowa kwota nie wyświetla mylącego wyniku kontroli', () => {
    assert.equal(projectedDeduction(retention,'abc'),null); assert.equal(projectedDeduction(null,'1'),null);
});
test('Potwierdzone i niezgodne potrącenia są różnymi stanami', () => {
    for(const status of ['MISMATCH','CONFIRMED','MATCHED','NOT_STARTED']) assert.equal(readDeductionReview({...retention,status}).status,status);
    assert.throws(()=>readDeductionReview({...retention,expectedAmount:'abc'}));
});
test('Nazwa wyświetlana jest niezależna od źródłowej i zachowuje rozszerzenie przy pobieraniu', () => {
    assert.equal(attachmentTitle(file),'Oświadczenie firmy A'); assert.equal(attachmentDownloadName(file),'Oświadczenie firmy A.pdf');
    assert.equal(file.originalFilename,'scan.pdf');
    assert.equal(attachmentDownloadName({...file,displayName:'Oświadczenie.PDF'}),'Oświadczenie.PDF');
});
test('Pobierana nazwa nie zawiera ścieżki z nazwy wyświetlanej', () => {
    assert.ok(!attachmentDownloadName({...file,displayName:'../../dokument'}).includes('/'));
    assert.ok(attachmentDownloadName({...file,displayName:'dokument.xml'}).endsWith('.xml.pdf'));
});
test('Zmiana nazwy zawiera wersję metadanych, bez zmiany pliku źródłowego', () => {
    assert.deepEqual(renameAttachmentRequest(file,'  Potwierdzenie przelewu  '),{method:'put',url:'/attachments/1/name',data:{displayName:'Potwierdzenie przelewu',version:2}});
});
test('Zmiana nazwy odrzuca pustą wartość, znaki sterujące i zły identyfikator', () => {
    for(const name of ['',' ','x'.repeat(256),'a\nb']) assert.throws(()=>renameAttachmentRequest(file,name));
    assert.throws(()=>renameAttachmentRequest({...file,id:'../1'},'Nazwa'));
});
test('Kategorie i wyszukiwanie filtrują nazwę, źródło i właściciela', () => {
    const rows=[{attachment:file,ownerLabel:'Faktura PW 4/2026'},{attachment:{...file,id:2,type:'CONTRACT',displayName:'Umowa'},ownerLabel:'Umowa główna'}];
    assert.equal(filterContractAttachments(rows,'SUBCONTRACTOR_STATEMENT','firmy a').length,1);
    assert.equal(filterContractAttachments(rows,'','4/2026').length,1);
    assert.equal(filterContractAttachments(rows,'CONTRACT','firmy a').length,0);
});
const contract={id:7,contractDate:'2026-01-01'}, form={imposedDate:'2026-10-18',amount:'123,45',description:' Zwłoka '};
test('Kara jest zapisywana z dokładnością do grosza i opisem', () => {
    assert.deepEqual(penaltyRequest(contract,null,form),{method:'post',url:'/contract-penalties',data:{contractId:7,imposedDate:'2026-10-18',amount:'123.45',description:'Zwłoka'}});
    assert.equal(penaltyRequest(contract,3,form).method,'put');
});
test('Kara nie może mieć daty sprzed umowy, zera ani zbyt dokładnej kwoty', () => {
    assert.ok(validatePenalty({...form,imposedDate:'2025-12-31'},contract.contractDate).imposedDate);
    for(const amount of ['0','-1','1.001','10000000000000']) assert.ok(validatePenalty({...form,amount},contract.contractDate).amount);
});
test('Lista kar nie miesza umów i nie akceptuje powielonych pozycji', () => {
    const row={id:3,contractId:7,imposedDate:form.imposedDate,amount:'123.45'};
    assert.equal(readPenalties([row],7).length,1);
    assert.throws(()=>readPenalties([row,row],7)); assert.throws(()=>readPenalties([row],8));
});
