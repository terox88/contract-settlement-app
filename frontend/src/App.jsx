import {
    Navigate,
    Route,
    Routes,
} from 'react-router-dom';

import InvestmentTaskListPage
    from './pages/InvestmentTaskListPage';

import InvestmentTaskDetailsPage
    from './pages/InvestmentTaskDetailsPage';

import MainContractDetailsPage
    from './pages/MainContractDetailsPage';

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
                path="/tasks/:taskId"
                element={
                    <InvestmentTaskDetailsPage />
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