/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import React from 'react';
import './App.css';

import {PageLayout, ThemeProvider} from '@primer/react'
import TurHeaderComponent from "./components/TurHeaderComponent";
import TurRootComponent from "./components/TurRootComponent";

export default class App extends React.Component {
    render() {
        return (
            <ThemeProvider>
                <PageLayout padding={"none"}>
                    <PageLayout.Header divider={"none"} padding={"none"}>
                        <TurHeaderComponent/>
                    </PageLayout.Header>
                    <PageLayout.Content padding={"none"}>
                        <TurRootComponent/>
                    </PageLayout.Content>
                    <PageLayout.Footer>
                        Footer
                    </PageLayout.Footer>
                </PageLayout>
            </ThemeProvider>
        );
    }
}
