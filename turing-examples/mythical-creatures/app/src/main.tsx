import React from "react";
import { createRoot } from "react-dom/client";
import { HashRouter } from "react-router-dom";
import axios from "axios";
import App from "./App.tsx";
import "./index.css";

axios.defaults.baseURL = import.meta.env.VITE_API_URL;

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </React.StrictMode>
);
