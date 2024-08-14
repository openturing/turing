import React, {useEffect, useState} from 'react';
import {CrudProps} from '../models/crud-props';
import {TurNLPInstance} from "../models/nlp-instance.model";
import {Heading, Label} from "@primer/react";

const NlpInstances = (props: CrudProps<TurNLPInstance>) => {
    const [nlpInstances, setNlpInstances] = useState<TurNLPInstance[]>([]);
    const [name, setName] = useState<string>('');
    const [accomplishment, setAccomplishment] = useState<string>('');

    const resetInputs = () => {
        setName('');
        setAccomplishment('');
    };

    useEffect(() => {
        const getNlpInstances = () =>
            props.get('api/nlp').subscribe(((response: React.SetStateAction<TurNLPInstance[]>) => {
                setNlpInstances(response);
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
                nlpInstances.map(nlpInstance =>
                    <div className="item" key={nlpInstance.id}>
                        <Heading>{nlpInstance.title}</Heading>
                        <div>{nlpInstance.description}</div>
                        <Label>Actived</Label>
                        <div className="text-gray-light d-flex flex-items-center" style={{marginTop: "10px"}}>
                            <div className="mr-3"><span
                                style={{
                                    position: "relative",
                                    top: "1px",
                                    display: "inline-block",
                                    width: "12px",
                                    height: "12px",
                                    borderRadius: "50%",
                                    backgroundColor: "#2b7489"
                                }}></span><span
                                className="ml-2">NLP</span></div>
                        </div>
                    </div>
                )
            }
        </>
    );
};


export default NlpInstances;
