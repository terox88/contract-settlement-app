export function uniqueInvoicePayments(rows) {
    return [...new Map(rows.map((row) => [String(row.paymentId), row])).values()];
}

export function buildStatementRequest(invoice, statementId, form) {
    const date = form.statementDate;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date ?? '') || Number.isNaN(Date.parse(date))
        || new Date(date).toISOString().slice(0, 10) !== date) throw new Error('Podaj poprawną datę oświadczenia.');
    if (date < invoice.issueDate) throw new Error('Data oświadczenia nie może być wcześniejsza od daty wystawienia faktury.');
    if ((form.remarks ?? '').trim().length > 1000) throw new Error('Uwagi mogą mieć najwyżej 1000 znaków.');
    if (!Number.isSafeInteger(Number(invoice.id)) || Number(invoice.id) < 1) throw new Error('Nieprawidłowa faktura oświadczenia.');
    if (statementId != null && !/^[1-9]\d*$/.test(String(statementId))) throw new Error('Nieprawidłowe oświadczenie.');
    const data = { statementDate: date, remarks: form.remarks?.trim() || null };
    return statementId ? { method: 'put', url: `/subcontractor-statements/${statementId}`, data }
        : { method: 'post', url: '/subcontractor-statements', data: { ...data, subcontractorInvoiceId: Number(invoice.id) } };
}
