package pl.company.settlements.file.model;

public record ContractAttachmentResponse(AttachmentResponse attachment, String ownerLabel,
                                         String ownerType, Long ownerId, Long subcontractId) { }
