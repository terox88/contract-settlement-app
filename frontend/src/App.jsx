import {
    Navigate,
    Route,
    Routes,
} from 'react-router-dom';

import InvestmentTaskListPage
    from './pages/InvestmentTaskListPage';

import InvestmentTaskDetailsPage
    from './pages/InvestmentTaskDetailsPage';

import InvestmentTaskFormPage
    from './pages/InvestmentTaskFormPage';

import './pages/InvestmentTaskFormPage.css';

import MainContractDetailsPage
    from './pages/MainContractDetailsPage';

import MainContractFormPage
    from './pages/MainContractFormPage';

import ContractAmendmentFormPage
    from './pages/ContractAmendmentFormPage';

import SubcontractFormPage
    from './pages/SubcontractFormPage';

import SubcontractDetailsPage from './pages/SubcontractDetailsPage';
import PaymentsPage from './pages/PaymentsPage';
import PaymentFormPage from './pages/PaymentFormPage';
import PaymentDetailsPage from './pages/PaymentDetailsPage';
import InvoicePaymentsPage from './pages/InvoicePaymentsPage';

import MainContractorInvoicesPage from './pages/MainContractorInvoicesPage';
import MainContractorInvoiceFormPage from './pages/MainContractorInvoiceFormPage';
import MainInvoiceSettlementPage from './pages/MainInvoiceSettlementPage';

import SubcontractorInvoicesPage from './pages/SubcontractorInvoicesPage';
import SubcontractorInvoiceFormPage from './pages/SubcontractorInvoiceFormPage';

export default function App() {
    return (
        <Routes>
            <Route path="/main-contracts/:contractId/subcontracts/:subcontractId" element={<SubcontractDetailsPage />} />
            <Route path="/main-contracts/:contractId/subcontracts/:parentSubcontractId/children/new" element={<SubcontractFormPage />} />
            <Route path="/main-contracts/:contractId/subcontracts/:subcontractId/amendments/new" element={<ContractAmendmentFormPage />} />
            <Route path="/main-contracts/:contractId/subcontracts/:subcontractId/amendments/:amendmentId/edit" element={<ContractAmendmentFormPage />} />
            <Route path="/main-contracts/:contractId/subcontracts/:subcontractId/invoices/:invoiceId/settlements" element={<MainInvoiceSettlementPage />} />

            <Route
                path="/"
                element={
                    <Navigate
                        to="/tasks"
                        replace
                    />
                }
            />

            <Route
                path="/tasks"
                element={
                    <InvestmentTaskListPage />
                }
            />

            <Route
                path="/tasks/new"
                element={
                    <InvestmentTaskFormPage />
                }
            />

            <Route
                path="/tasks/:taskId/edit"
                element={
                    <InvestmentTaskFormPage />
                }
            />

            <Route
                path="/tasks/:taskId"
                element={
                    <InvestmentTaskDetailsPage />
                }
            />

            <Route
                path="/tasks/:taskId/main-contracts/new"
                element={
                    <MainContractFormPage />
                }
            />

            <Route
                path="/main-contracts/:contractId/edit"
                element={
                    <MainContractFormPage />
                }
            />

            <Route
                path="/main-contracts/:contractId/amendments/new"
                element={
                    <ContractAmendmentFormPage />
                }
            />

            <Route
                path="/main-contracts/:contractId/amendments/:amendmentId/edit"
                element={
                    <ContractAmendmentFormPage />
                }
            />

            <Route
                path="/main-contracts/:contractId/subcontracts/new"
                element={
                    <SubcontractFormPage />
                }
            />

            <Route
                path="/main-contracts/:contractId/subcontracts/:subcontractId/edit"
                element={
                    <SubcontractFormPage />
                }
            />

            <Route
                path="/main-contracts/:contractId/subcontracts/:subcontractId/invoices"
                element={<SubcontractorInvoicesPage />}
            />
            <Route
                path="/main-contracts/:contractId/subcontracts/:subcontractId/invoices/new"
                element={<SubcontractorInvoiceFormPage />}
            />
            <Route
                path="/main-contracts/:contractId/subcontracts/:subcontractId/invoices/:invoiceId/edit"
                element={<SubcontractorInvoiceFormPage />}
            />

            <Route path="/payments" element={<PaymentsPage />} />
            <Route path="/payments/new" element={<PaymentFormPage />} />
            <Route path="/payments/:paymentId" element={<PaymentDetailsPage />} />
            <Route path="/payments/:paymentId/edit" element={<PaymentFormPage />} />
            <Route path="/main-contracts/:contractId/invoices/:mainInvoiceId/payments" element={<InvoicePaymentsPage />} />
            <Route path="/main-contracts/:contractId/subcontracts/:subcontractId/invoices/:invoiceId/payments" element={<InvoicePaymentsPage />} />

            <Route path="/main-contracts/:contractId/invoices" element={<MainContractorInvoicesPage />} />
            <Route path="/main-contracts/:contractId/invoices/new" element={<MainContractorInvoiceFormPage />} />
            <Route path="/main-contracts/:contractId/invoices/:mainInvoiceId" element={<MainInvoiceSettlementPage />} />
            <Route path="/main-contracts/:contractId/invoices/:mainInvoiceId/edit" element={<MainContractorInvoiceFormPage />} />

            <Route
                path="/main-contracts/:contractId"
                element={
                    <MainContractDetailsPage />
                }
            />

        </Routes>
    );
}
