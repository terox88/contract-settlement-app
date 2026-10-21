import { Component } from 'react';
import { Link } from 'react-router-dom';

export default class ViewErrorBoundary extends Component {
    state = { failed: false };

    static getDerivedStateFromError() {
        return { failed: true };
    }

    render() {
        if (!this.state.failed) return this.props.children;
        return <section className="card" role="alert">
            <h2>Nie udało się wczytać widoku</h2>
            <p>Sprawdź połączenie i odśwież aplikację. Jeżeli problem się powtarza, skontaktuj się z administratorem.</p>
            <div className="main-form-contract-actions">
                <button type="button" className="main-form-button" onClick={() => window.location.reload()}>Odśwież aplikację</button>
                <Link className="main-form-button" to="/tasks">Lista zadań</Link>
            </div>
        </section>;
    }
}
