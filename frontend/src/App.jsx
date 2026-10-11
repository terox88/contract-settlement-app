import {
    Navigate,
    Route,
    Routes,
} from 'react-router-dom';

import InvestmentTaskListPage
    from './pages/InvestmentTaskListPage';

import InvestmentTaskDetailsPage
    from './pages/InvestmentTaskDetailsPage';

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

        </Routes>
    );
}