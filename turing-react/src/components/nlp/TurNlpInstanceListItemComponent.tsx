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

import React, {useEffect, useState} from 'react';
import {TurNLPInstance} from "../../models/nlp-instance.model";
import {ActionList, Label} from "@primer/react";
import {CommentDiscussionIcon} from "@primer/octicons-react";
import {TurListProps} from "../../models/TurListProps";

function TurNlpInstanceListItemComponent(props: TurListProps<TurNLPInstance>) {
    const [nlpInstances, setNlpInstances] = useState<TurNLPInstance[]>([]);

    useEffect(() => {
        const getNlpInstances = () =>
            props.getAll('api/nlp').subscribe(((response: React.SetStateAction<TurNLPInstance[]>) => {
                if (response) {
                    setNlpInstances(response);
                } else {
                    setNlpInstances(new Array<TurNLPInstance>())
                }
            }));
        getNlpInstances();

        const changeSubscription = props
            .change?.subscribe((change: any) => {
                if (change) getNlpInstances();
            });

        return (() => {
            changeSubscription?.unsubscribe();
        })
    }, []);

    return (
        <>
            {
                nlpInstances
                    .map(nlpInstance =>
                        <ActionList.LinkItem href={`/nlp/instance/${nlpInstance.id}`} aria-keyshortcuts="g">
                            <ActionList.LeadingVisual>
                                <CommentDiscussionIcon/>
                            </ActionList.LeadingVisual>
                            {nlpInstance.title}
                            <ActionList.Description variant="block">
                                <div>{nlpInstance.description}</div>

                                <div className="text-gray-light d-flex-left" style={{marginTop: "10px"}}>
                                    <Label>Actived</Label>
                                    <div style={{marginTop: "3px"}}><span
                                        style={{
                                            position: "relative",
                                            top: "2px",
                                            display: "inline-block",
                                            width: "12px",
                                            height: "12px",
                                            borderRadius: "50%",
                                            backgroundColor: "#2b7489"
                                        }}></span>
                                        <span
                                            style={{marginLeft: "10px"}}>NLP</span></div>
                                </div>
                            </ActionList.Description>
                        </ActionList.LinkItem>
                    )
            }
        </>
    );
}

export default TurNlpInstanceListItemComponent;
