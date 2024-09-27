
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

import React, {useEffect, useState} from "react";
import {useParams } from "react-router-dom";
import {Box, Button, Checkbox, FormControl, Header, Select, TextInput} from "@primer/react";
import {TrashIcon} from "@primer/octicons-react";

import {TurNLPInstance} from "../../models/nlp-instance.model";
import {TurCrudProps} from "../../models/TurCrudProps";

export default function TurNlpInstanceComponent(props: TurCrudProps<TurNLPInstance>)  {
    let {id} = useParams();
    const [nlpInstance, setNlpInstance] = useState<TurNLPInstance | null>(null);

    useEffect(() => {
        const getNlpInstance = () =>
            props.get(`api/nlp`, id).subscribe(((response: React.SetStateAction<TurNLPInstance | null>) => {
                    setNlpInstance(response);
            }));
        getNlpInstance();

        const changeSubscription = props
            .change?.subscribe((change: any) => {
                if (change) getNlpInstance();
            });

        return (() => {
            changeSubscription?.unsubscribe();
        })
    }, []);
    return (<>
            <Header style={{backgroundColor: "white", color: "black"}}>
                <Header.Item><h1>{nlpInstance?.title}</h1></Header.Item>
                <Header.Item full></Header.Item>
                <Header.Item sx={{
                    mr: 0
                }}> <Button variant={"danger"} leadingVisual={TrashIcon} sx={{mr: 2}}>Delete</Button>
                    <Button variant={"primary"}>Update NLP Instance</Button>
                </Header.Item>


            </Header>
            <Box sx={{
                display: 'flex',
                flexDirection: 'column',
                gap: '1rem'
            }}>
                <FormControl required={true}>
                    <FormControl.Label requiredIndicator={false}>Title</FormControl.Label>
                    <FormControl.Caption>NLP instance title will appear on NLP list.</FormControl.Caption>
                    <TextInput block
                               value={nlpInstance?.title}/>
                </FormControl>
                <FormControl required={false}>
                    <FormControl.Label requiredIndicator={false}>Description</FormControl.Label>
                    <FormControl.Caption>NLP instance description will appear on NLP list.</FormControl.Caption>
                    <TextInput block value={nlpInstance?.description}/>
                </FormControl>
                <FormControl required={true}>
                    <FormControl.Label requiredIndicator={false}>Vendor</FormControl.Label>
                    <FormControl.Caption>NLP vendor that will be used.</FormControl.Caption>
                    <Select block>
                        <Select.Option value="figma">Figma</Select.Option>
                        <Select.Option value="css">Primer CSS</Select.Option>
                        <Select.Option value="prc">Primer React components</Select.Option>
                        <Select.Option value="pvc">Primer ViewComponents</Select.Option>
                    </Select>
                </FormControl>
                <FormControl required={false}>
                    <FormControl.Label requiredIndicator={false}>Endpoint URL</FormControl.Label>
                    <FormControl.Caption>NLP instance endpoint url will be connected.</FormControl.Caption>
                    <TextInput block value={nlpInstance?.endpointURL}/>
                </FormControl>
                <FormControl required={false}>
                    <FormControl.Label requiredIndicator={false}>Key</FormControl.Label>
                    <FormControl.Caption>NLP instance secret key that will be used to connect.</FormControl.Caption>
                    <TextInput block value={nlpInstance?.key}/>
                </FormControl>
                <FormControl required={true}>
                    <FormControl.Label requiredIndicator={false}>Language</FormControl.Label>
                    <FormControl.Caption>Language that supports NLP.</FormControl.Caption>
                    <TextInput block value={nlpInstance?.language}/>
                </FormControl>
                <FormControl required={false}>
                    <FormControl.Label requiredIndicator={false}>Enabled</FormControl.Label>
                    <FormControl.Caption>If this NLP Instance will be used in others Turing AI Components.</FormControl.Caption>
                    <Checkbox />
                </FormControl>
            </Box>
        </>
    )
}
