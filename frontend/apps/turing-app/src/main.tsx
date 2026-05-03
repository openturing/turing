import '@/i18n';
import '@/lib/axios'; // Configure axios interceptors for CSRF + backend status
import { turingQueryClient } from '@/lib/query-client';
import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { BackendStatusProvider, ErrorBoundary } from '@viglet/viglet-design-system';
import axios from 'axios';
import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.tsx';
import './index.css';

axios.defaults.baseURL = `${import.meta.env.VITE_API_URL}/api`;

axios.interceptors.request.use(async (config) => {
  if (!(config.data instanceof FormData)) {
    config.headers['Content-Type'] = 'application/json';
  }
  config.headers['X-Requested-With'] = 'XMLHttpRequest';
  return config;
});

// `?loading=1` holds the index.html boot loader for design/regression testing
// (see the guard in index.html). Bail out before React mounts — the static
// markup inside `#root` stays visible, animations keep playing.
if (!(globalThis as Record<string, unknown>).__TURING_LOADING_TEST__) {
  createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <BrowserRouter>
        <QueryClientProvider client={turingQueryClient}>
          <ErrorBoundary>
            <BackendStatusProvider healthEndpoint="/api/v2/ping">
              <App />
            </BackendStatusProvider>
          </ErrorBoundary>
          {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
        </QueryClientProvider>
      </BrowserRouter>
    </React.StrictMode>
  )
}
