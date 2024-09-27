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

import {ActionList, Button, Header, TextInput} from "@primer/react";
import {PackageIcon, SearchIcon, UploadIcon} from "@primer/octicons-react";
import TurNlpInstanceListItemComponent from "./TurNlpInstanceListItemComponent";
import React from "react";
import {TurListProps} from "../../models/TurListProps";
import {TurNLPInstance} from "../../models/nlp-instance.model";

export default class TurNlpInstanceListComponent extends React.Component<TurListProps<TurNLPInstance>>
{
    render() {
        return <>
            <Header style={{backgroundColor: "currentcolor"}}>
                <Header.Item> <TextInput
                    leadingVisual={SearchIcon}
                    aria-label="NLP Instance"
                    name="nlpinstance"
                    placeholder="Find a NLP Instance"
                    autoComplete="nlp-instance"
                /></Header.Item>
                <Header.Item full>Item 2</Header.Item>
                <Header.Item sx={{
                    mr: 0
                }}> <Button leadingVisual={UploadIcon} sx={{mr: 2}}>Import</Button>
                    <Button leadingVisual={PackageIcon} variant={"primary"}>New</Button>
                </Header.Item>
            </Header>
            <ActionList showDividers>
                <TurNlpInstanceListItemComponent getAll={this.props.getAll} change={this.props.change}/>
            </ActionList>
        </>;
    }
}
