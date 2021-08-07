import { ThemeProvider } from '@primer/components'
import React from 'react';
import DashboardPage from './page/dashboard-page'
import VigHeader from './components/vig-header'

function App() {
  return (
    <ThemeProvider>
      <VigHeader></VigHeader>
      <DashboardPage></DashboardPage>
    </ThemeProvider>
  );
}

export default App;
