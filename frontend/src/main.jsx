import React from 'react';
import { createRoot } from 'react-dom/client';
import { Security } from '@okta/okta-react';
import { BrowserRouter } from 'react-router-dom';
import { oktaAuth } from './auth';
import App from './App';
import './styles.css';

const restoreOriginalUri = (_oktaAuth, originalUri) => {
  window.location.replace(originalUri || window.location.origin);
};

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <Security oktaAuth={oktaAuth} restoreOriginalUri={restoreOriginalUri}>
        <App />
      </Security>
    </BrowserRouter>
  </React.StrictMode>
);

