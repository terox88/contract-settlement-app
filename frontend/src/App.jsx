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

export default function App() {
    return (
        <Routes>

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
                path="/main-contracts/:contractId"
                element={
                    <MainContractDetailsPage />
                }
            />

        </Routes>
    );
}
