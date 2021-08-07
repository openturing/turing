import { ThemeProvider } from '@primer/components'
import React from 'react';
import VigHeader from '../components/vig-header'

function App() {
  return (
    <ThemeProvider>
      <VigHeader></VigHeader>
      <h1>Hi everybody</h1>
    </ThemeProvider>
  );
}

export default App;
