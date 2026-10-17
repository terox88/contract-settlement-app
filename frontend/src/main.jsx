import React from 'react';
import ReactDOM from 'react-dom/client';
import {
    BrowserRouter,
} from 'react-router-dom';

import App from './App';
import TaskNavigation from './TaskNavigation';
import './styles.css';

ReactDOM
    .createRoot(
        document.getElementById('root')
    )
    .render(
        <React.StrictMode>
            <BrowserRouter>
                <main className="container">
                    <TaskNavigation />
                    <App />
                </main>
            </BrowserRouter>
        </React.StrictMode>
    );
