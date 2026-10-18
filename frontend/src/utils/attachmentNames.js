export function attachmentTitle(item) { return item.displayName?.trim() || item.originalFilename; }
export function attachmentDownloadName(item) {
    let name = attachmentTitle(item).replace(/[\\/:*?"<>|\x00-\x1f\x7f]/g, '_').replace(/^\.+/, '').trim().slice(0, 200) || 'dokument';
    const extension = /\.[A-Za-z0-9]{1,10}$/.exec(item.originalFilename)?.[0] ?? '';
    return extension && !name.toLowerCase().endsWith(extension.toLowerCase()) ? name + extension.toLowerCase() : name;
}
export function renameAttachmentRequest(item, value) {
    const displayName = value.trim();
    if (!displayName || displayName.length > 255 || /[\x00-\x1f\x7f-\x9f]/.test(displayName)) throw new Error('Podaj nazwę do 255 znaków, bez znaków sterujących.');
    if (!/^[1-9]\d*$/.test(String(item.id)) || !Number.isSafeInteger(item.metadataVersion ?? 0) || (item.metadataVersion ?? 0) < 0) throw new Error('Nieprawidłowe dane dokumentu. Odśwież listę.');
    return { method: 'put', url: `/attachments/${item.id}/name`, data: { displayName, version: item.metadataVersion ?? 0 } };
}
export function readContractAttachments(data) {
    if (!Array.isArray(data) || data.some(row => !row?.attachment || typeof row.ownerLabel !== 'string' || !row.ownerType || !row.ownerId)) throw new Error('Nie można odczytać dokumentów umowy.');
    return data;
}
export function filterContractAttachments(rows, type, query) {
    const needle = query.trim().toLocaleLowerCase('pl-PL');
    return rows.filter(row => (!type || row.attachment.type === type) && (!needle
        || [attachmentTitle(row.attachment), row.attachment.originalFilename, row.attachment.description, row.ownerLabel].join(' ').toLocaleLowerCase('pl-PL').includes(needle)));
}
