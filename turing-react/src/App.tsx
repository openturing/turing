import React from 'react';
import './App.css';
import {Box, Heading, ThemeProvider} from '@primer/react'

function TurBase() {
    return (
        <Box m={4}>
            <Heading as="h2" sx={{mb: 2}}>
                Hello, world!
            </Heading>
            <p>This will get Primer text styles.</p>
        </Box>
    )
}
function App() {
    return (
        <ThemeProvider>
            <TurBase/>
        </ThemeProvider>
    )
}

export default App;
