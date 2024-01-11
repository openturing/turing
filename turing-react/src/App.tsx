import React from 'react';
import './App.css';
import {Avatar, Header, ThemeProvider, } from '@primer/react'
function TurBase() {
    return (
        <Header>
            <Header.Item>
                <Header.Link href="#" fontSize={2}>
                    <span>GitHub</span>
                </Header.Link>
            </Header.Item>
            <Header.Item full>Menu</Header.Item>
            <Header.Item sx={{mr: 0}}>
                <Avatar src="https://github.com/octocat.png" size={20} square alt="@octocat" />
            </Header.Item>
        </Header>
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
