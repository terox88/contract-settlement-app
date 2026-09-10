CREATE TABLE investment_task (
    id BIGSERIAL PRIMARY KEY,
    task_number VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000)
);

CREATE TABLE contractor (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(255) NOT NULL,
    street VARCHAR(255),
    building_number VARCHAR(255),
    apartment_number VARCHAR(255),
    postal_code VARCHAR(255),
    city VARCHAR(255),
    country VARCHAR(255),
    email VARCHAR(255),
    CONSTRAINT uk_contractor_tax_id UNIQUE (tax_id)
);

CREATE TABLE contract (
    id BIGSERIAL PRIMARY KEY,
    contract_number VARCHAR(255) NOT NULL,
    contract_date DATE NOT NULL,
    initial_value NUMERIC(15,2) NOT NULL,
    initial_start_date DATE,
    initial_end_date DATE
);

CREATE TABLE main_contract (
    id BIGINT PRIMARY KEY,
    main_contractor_id BIGINT NOT NULL,
    investment_task_id BIGINT NOT NULL,
    CONSTRAINT fk_main_contract_contract FOREIGN KEY (id) REFERENCES contract(id),
    CONSTRAINT fk_main_contract_contractor FOREIGN KEY (main_contractor_id) REFERENCES contractor(id),
    CONSTRAINT fk_main_contract_task FOREIGN KEY (investment_task_id) REFERENCES investment_task(id)
);

CREATE TABLE subcontract (
    id BIGINT PRIMARY KEY,
    subcontractor_id BIGINT NOT NULL,
    main_contract_id BIGINT NOT NULL,
    CONSTRAINT fk_subcontract_contract FOREIGN KEY (id) REFERENCES contract(id),
    CONSTRAINT fk_subcontract_contractor FOREIGN KEY (subcontractor_id) REFERENCES contractor(id),
    CONSTRAINT fk_subcontract_main_contract FOREIGN KEY (main_contract_id) REFERENCES main_contract(id)
);

CREATE TABLE contract_amendment (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    amendment_number VARCHAR(255) NOT NULL,
    amendment_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    value_change NUMERIC(15,2),
    new_start_date DATE,
    new_end_date DATE,
    description VARCHAR(2000),
    CONSTRAINT fk_amendment_contract FOREIGN KEY (contract_id) REFERENCES contract(id),
    CONSTRAINT uk_contract_effective_date UNIQUE (contract_id, effective_date)
);

CREATE TABLE stored_file (
    id BIGSERIAL PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(255),
    size BIGINT,
    uploaded_at TIMESTAMP NOT NULL
);

CREATE TABLE document_attachment (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT fk_document_attachment_file FOREIGN KEY (file_id) REFERENCES stored_file(id)
);

CREATE INDEX idx_main_contract_task ON main_contract(investment_task_id);
CREATE INDEX idx_subcontract_main_contract ON subcontract(main_contract_id);
CREATE INDEX idx_amendment_contract ON contract_amendment(contract_id);
