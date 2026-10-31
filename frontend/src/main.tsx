import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { AuthProvider } from './context/AuthContext';
import { Router } from './components/routing/Router';
import './index.css';
import './styles/operational-refresh.css';
import './pages/configuration/ConfigurationWorkspace.css';
import './pages/positions/PositionsWorkspace.css';
import './pages/employees/EmployeesWorkspace.css';
import './pages/unavailabilities/UnavailabilitiesWorkspace.css';
import './pages/unavailabilities/UnavailabilityDialog.css';
import './styles/catalog-dialog.css';
import './pages/home/HomeComposition.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider>
      <Router>
        <App />
      </Router>
    </AuthProvider>
  </React.StrictMode>
);
