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

import {NavList, Octicon, PageLayout} from "@primer/react";
import {CommentDiscussionIcon} from "@primer/octicons-react";
import React from "react";
import {Route, Routes} from "react-router-dom";
import TurNlpInstanceListComponent from "./TurNlpInstanceListComponent";
import TurNlpInstanceComponent from "./TurNlpInstanceComponent";
import nlpInstanceService from "../../service/nlp-instance.service";



export default class TurNlpInstancePage extends React.Component {
    render() {
        const {engineerChange, getAll, getItem, addItem, updateItem, deleteItem} = nlpInstanceService();
        return <PageLayout padding={"none"}>
            <PageLayout.Header divider={"line"} padding={"none"}>
                <Octicon icon={CommentDiscussionIcon} size={32} sx={{mr: 2}}/> NLP
            </PageLayout.Header>
            <PageLayout.Pane position="start" divider={"none"} padding={"none"}>
                <NavList>
                    <NavList.Item href="/nlp/instance" aria-current="page">
                        Instances
                    </NavList.Item>
                    <NavList.Item href="/nlp/entity">Entity</NavList.Item>
                </NavList>
            </PageLayout.Pane>
            <PageLayout.Content>
                <Routes>
                        <Route path="" element={<TurNlpInstanceListComponent getAll={getAll}
                                                                             change={engineerChange}/>}/>
                        <Route path=":id" element={<TurNlpInstanceComponent add={addItem}
                                                                            change={engineerChange}
                                                                            delete={deleteItem}
                                                                            get={getItem}
                                                                            update={updateItem}/>}/>
                </Routes>

            </PageLayout.Content>
        </PageLayout>;
    }
}
