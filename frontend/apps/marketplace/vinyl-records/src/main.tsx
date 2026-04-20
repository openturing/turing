import React from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import axios from 'axios'
import App from './App.tsx'
import './index.css'

axios.defaults.baseURL = import.meta.env.VITE_API_URL;

// Transfer server-side query params (e.g. ?_setlocale=en_US&q=dragon)
// into the hash so HashRouter's useSearchParams can read them.
if (window.location.search && !window.location.hash.includes("?")) {
  const params = window.location.search.substring(1);
  const hash = window.location.hash || "#/";
  const separator = hash.includes("?") ? "&" : "?";
  window.history.replaceState(null, "", window.location.pathname + hash + separator + params);
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </React.StrictMode>
)
