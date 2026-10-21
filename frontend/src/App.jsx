import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import InvestmentTaskListPage from './pages/InvestmentTaskListPage';
import ViewErrorBoundary from './ViewErrorBoundary';
import './pages/InvestmentTaskFormPage.css';
import './pages/MainContractFormPage.css';

const InvestmentTaskDetailsPage = lazy(() => import('./pages/InvestmentTaskDetailsPage'));
const InvestmentTaskFormPage = lazy(() => import('./pages/InvestmentTaskFormPage'));
const MainContractDetailsPage = lazy(() => import('./pages/MainContractDetailsPage'));
const MainContractFormPage = lazy(() => import('./pages/MainContractFormPage'));
const ContractAmendmentFormPage = lazy(() => import('./pages/ContractAmendmentFormPage'));
const SubcontractFormPage = lazy(() => import('./pages/SubcontractFormPage'));
const SubcontractDetailsPage = lazy(() => import('./pages/SubcontractDetailsPage'));
const ContractAttachmentsPage = lazy(() => import('./pages/ContractAttachmentsPage'));
const PaymentsPage = lazy(() => import('./pages/PaymentsPage'));
const PaymentFormPage = lazy(() => import('./pages/PaymentFormPage'));
const PaymentDetailsPage = lazy(() => import('./pages/PaymentDetailsPage'));
const InvoicePaymentsPage = lazy(() => import('./pages/InvoicePaymentsPage'));
const MainContractorInvoicesPage = lazy(() => import('./pages/MainContractorInvoicesPage'));
const MainContractorInvoiceFormPage = lazy(() => import('./pages/MainContractorInvoiceFormPage'));
const MainInvoiceSettlementPage = lazy(() => import('./pages/MainInvoiceSettlementPage'));
const SubcontractorInvoicesPage = lazy(() => import('./pages/SubcontractorInvoicesPage'));
const SubcontractorInvoiceFormPage = lazy(() => import('./pages/SubcontractorInvoiceFormPage'));

export default function App() {
    const location = useLocation();
    return (
        <ViewErrorBoundary key={location.pathname}>
            <Suspense fallback={<div className="loading-message" role="status">Ładowanie widoku…</div>}>
        <Routes>
            <Route path="/main-contracts/:contractId/attachments" element={<ContractAttachmentsPage />} />
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
            </Suspense>
        </ViewErrorBoundary>
    );
}
