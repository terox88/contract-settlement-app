# Contract Settlement App

A full-stack web application for managing and monitoring construction contract settlements.

The project was created to model a real-world business process involving investment tasks, main contracts, subcontractors, invoices, payments, financial limits, amendments, penalties, and supporting documents.

The application focuses on financial transparency and makes it possible to track how much has been contracted, invoiced, paid, and still remains to be settled.

---

## Highlights

- Complex business domain modeled with JPA entities and relationships
- REST API built with Spring Boot
- PostgreSQL database managed with Flyway migrations
- Payment allocation between bank payments and invoices
- Financial calculations at contract and investment-task level
- Multi-year investment budgets
- Main contractor and subcontractor settlement flows
- KSeF XML invoice import
- PDF attachment processing with page selection and document splitting
- Filtering using Spring Data JPA Specifications
- Backend validation and centralized exception handling
- React frontend consuming the REST API
- Docker-based local deployment

---

## Business Flow

A typical main contractor settlement flow:

```text
Investment Task
    ↓
Main Contract
    ↓
Main Contractor Invoice
    ↓
Payment Allocation
    ↓
Payment
```

Subcontractor settlement flow:

```text
Main Contract
    ↓
Subcontract
    ↓
Subcontractor Invoice
    ↓
Payment Allocation
    ↓
Subcontractor Statement
```

The system supports cases where:

- one payment covers multiple invoices,
- one invoice is paid using multiple payments,
- deductions are tracked separately from actually paid amounts,
- contract values change through amendments,
- investment budgets span multiple years.

---

## Financial Tracking

### Investment Task

The application calculates:

- Total budget
- Current year budget
- Total amount paid
- Amount paid in the current year
- Remaining total budget
- Remaining current year budget

### Main Contract

For every main contract the system tracks:

- Initial contract value
- Current contract value after amendments
- Invoiced amount
- Paid amount
- Remaining amount to invoice
- Remaining amount to pay
- Contract penalties

This allows the application to provide a quick financial overview without storing redundant calculated values in the database.

---

## Technology Stack

### Backend

- Java 21
- Spring Boot 3
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- Gradle
- Lombok
- Bean Validation
- JUnit 5
- Mockito
- AssertJ
- Apache PDFBox

### Frontend

- React
- Vite
- React Router
- Axios
- CSS

### Infrastructure

- Docker
- Docker Compose
- PostgreSQL container
- Local persistent document storage

---

## Backend Structure

The backend is divided into domain-oriented modules:

```text
pl.company.settlements
├── task
├── contract
├── contractor
├── invoice
├── payment
├── budget
├── file
├── importing
├── common
└── config
```

Each module is separated into layers such as:

```text
api
domain
model
repository
service
```

This keeps business logic separated from HTTP controllers and persistence concerns.

---

## Payment Model

Payments are stored independently from invoices.

A payment can be allocated to either:

- a main contractor invoice,
- or a subcontractor invoice.

Example:

```text
Payment
    ↓
PaymentAllocation
    ├── MainContractorInvoice
    └── SubcontractorInvoice
```

Each allocation represents the part of the bank payment assigned to a specific invoice.

For subcontractor invoices, the application also supports deductions:

```text
remaining =
gross amount
- paid amount
- deducted amount
```

For main contractor invoices:

```text
remaining =
gross amount
- paid amount
```

---

## KSeF XML Import

The application supports importing invoices from KSeF XML files.

The XML is parsed into a preview before anything is stored in the database.

Extracted information includes:

- Invoice number
- Issue date
- Sale date
- Due date
- Seller
- Buyer
- Net amount
- VAT amount
- Gross amount
- VAT rates
- Currency

The user can review and edit the imported values before saving the invoice.

---

## Document Management

Documents can be attached to:

- Contracts
- Amendments
- Main contractor invoices
- Subcontractor invoices
- Payments
- Subcontractor statements

PDF documents support optional page selection.

Example:

```text
1-3,7,10-12
```

Only the selected pages are stored.

A single PDF can also be split into several separate attachments.

Example:

```text
1-3;4-7;10-12
```

---

## Database

PostgreSQL is used as the main database.

The database schema is managed entirely with Flyway migrations.

Hibernate is configured with:

```text
ddl-auto=validate
```

so schema changes are controlled explicitly through versioned migrations.

---

## Error Handling

The backend uses centralized exception handling and returns consistent API error responses.

Handled cases include:

- Resource not found
- Business conflicts
- Invalid arguments
- Validation errors
- Database constraint violations
- Unexpected server errors

---

## Frontend

The React frontend currently provides:

- Investment task list
- Task status filtering
- Investment task details
- Investment budget overview
- Main contract list
- Main contract details
- Contract-level financial summaries

Navigation currently follows:

```text
Investment Tasks
        ↓
Investment Task Details
        ↓
Main Contracts
        ↓
Main Contract Details
```

Further views will expose detailed management of invoices, subcontractors, amendments, financial limits, penalties, and attachments.

---

## Running the Project

The application can be started with Docker Compose:

```bash
docker compose up --build
```

The backend is available by default at:

```text
http://localhost:8080
```

During frontend development, Vite proxies `/api` requests to the Spring Boot backend.

---

## Project Goal

The goal of this project is not only to demonstrate CRUD operations, but to model a realistic business domain involving:

- financial relationships,
- multiple contract levels,
- settlement rules,
- calculated financial data,
- document processing,
- database consistency,
- and frontend/backend integration.

The project is actively developed and additional frontend workflows are being implemented.