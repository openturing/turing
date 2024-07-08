import React from 'react';
import './App.css';
import {
    Button,
    Header, Heading, Label,
    NavList,
    Octicon,
    Pagehead,
    PageLayout,
    TextInput,
    ThemeProvider,
    UnderlineNav,
} from '@primer/react'
import {
    CodeIcon,
    CodescanIcon,
    CommentDiscussionIcon,
    PackageIcon, PlugIcon, SearchIcon, UploadIcon
} from "@primer/octicons-react";
import {Observable, of, Subject} from "rxjs";
import api from "./axios/api";
import {catchError, take} from "rxjs/operators";
import {TurNLPInstance} from "./models/nlp-instance.model";
import NlpInstances from "./components/NLPInstance";

export const useObservable = () => {
    const subj = new Subject<boolean>();

    const next = (value:boolean): void => {
        subj.next(value) };

    return { change: subj.asObservable() , next};
};



function TurBase() {
    return (
        <Header>
            <Header.Item>
                <Header.Link href="#" fontSize={2}>
                    <span>Administration Console</span>
                </Header.Link>
            </Header.Item>
        </Header>
    )
}

export const App: React.FC = () => {
    const {change: engineerChange, next: engNext} = useObservable();
    const {change: languagesChange, next: lanNext} = useObservable();

    const getItem = <T,> (url: string): Observable<T[]> => {
        return api.get<T[]>(url)
            .pipe(
                take(1),
                catchError(err => of(console.log(err)))
            ) as Observable<T[]>;
    };

    const addItem = (url: string, item: (TurNLPInstance)):void => {
        api.post(url, item)
            .pipe(take(1))
            .subscribe(() => {
                url === 'nlp' ? engNext(true) : lanNext(true);
            });
    };

    const updateItem = (url :string, item:(TurNLPInstance)) => {
        api.put(url, item)
            .pipe(take(1))
            .subscribe(() => {
                url === 'nlp' ? engNext(true) : lanNext(true);
            });
    };

    const deleteItem = (url: string, id: string): void => {
        api.delete(url, id)
            .subscribe(() => {
                url === 'nlp' ? engNext(true) : lanNext(true);
            });
    };

    return (
        <ThemeProvider>
            <PageLayout>
                <PageLayout.Header>
                    <TurBase/>
                    <UnderlineNav aria-label="NLP">
                        <UnderlineNav.Item aria-current="page" icon={CommentDiscussionIcon}>
                            NLP
                        </UnderlineNav.Item>
                        <UnderlineNav.Item icon={CodescanIcon} counter={30}>
                            Search Engine
                        </UnderlineNav.Item>
                        <UnderlineNav.Item icon={SearchIcon} counter={3}>
                            Semantic Navigation
                        </UnderlineNav.Item>
                        <UnderlineNav.Item icon={PlugIcon}>Integration</UnderlineNav.Item>
                        <UnderlineNav.Item icon={CodeIcon} counter={9}>
                            API Token
                        </UnderlineNav.Item>
                    </UnderlineNav>
                </PageLayout.Header>

                <PageLayout.Content>
                    <Pagehead><Octicon icon={CommentDiscussionIcon} size={32} sx={{mr: 2}} /> NLP</Pagehead>
                    <PageLayout.Pane position="start" aria-label="Secondary navigation">
                        <NavList>
                            <NavList.Item href="/" aria-current="page">
                                Instances
                            </NavList.Item>
                            <NavList.Item href="/about">Entity</NavList.Item>
                        </NavList>
                    </PageLayout.Pane>
                    <div>
                        <TextInput
                            leadingVisual={SearchIcon}
                            aria-label="NLP Instance"
                            name="nlpinstance"
                            placeholder="Find a NLP Instance"
                            autoComplete="nlp-instance"
                        />
                        <Button leadingVisual={UploadIcon}>Import</Button>
                        <Button leadingVisual={PackageIcon} variant={'primary'}>New</Button>
                        <NlpInstances  get={getItem} delete={deleteItem} add={addItem} update={updateItem} change={engineerChange}/>
                    </div>
                    <div>

                    </div>
                </PageLayout.Content>
                <PageLayout.Pane>

                </PageLayout.Pane>
                <PageLayout.Footer>

                </PageLayout.Footer>
            </PageLayout>
        </ThemeProvider>
    );
};



function App2() {
    return (
        <ThemeProvider>
            <PageLayout>
                <PageLayout.Header>
                    <TurBase/>
                    <UnderlineNav aria-label="NLP">
                        <UnderlineNav.Item aria-current="page" icon={CommentDiscussionIcon}>
                            NLP
                        </UnderlineNav.Item>
                        <UnderlineNav.Item icon={CodescanIcon} counter={30}>
                            Search Engine
                        </UnderlineNav.Item>
                        <UnderlineNav.Item icon={SearchIcon} counter={3}>
                            Semantic Navigation
                        </UnderlineNav.Item>
                        <UnderlineNav.Item icon={PlugIcon}>Integration</UnderlineNav.Item>
                        <UnderlineNav.Item icon={CodeIcon} counter={9}>
                            API Token
                        </UnderlineNav.Item>
                    </UnderlineNav>
                </PageLayout.Header>

                <PageLayout.Content>
                    <Pagehead><Octicon icon={CommentDiscussionIcon} size={32} sx={{mr: 2}} /> NLP</Pagehead>
                    <PageLayout.Pane position="start" aria-label="Secondary navigation">
                        <NavList>
                            <NavList.Item href="/" aria-current="page">
                                Instances
                            </NavList.Item>
                            <NavList.Item href="/about">Entity</NavList.Item>
                        </NavList>
                    </PageLayout.Pane>
                    <div>
                        <TextInput
                            leadingVisual={SearchIcon}
                            aria-label="NLP Instance"
                            name="nlpinstance"
                            placeholder="Find a NLP Instance"
                            autoComplete="nlp-instance"
                        />
                        <Button leadingVisual={UploadIcon}>Import</Button>
                        <Button leadingVisual={PackageIcon} variant={'primary'}>New</Button>
                        <Heading>CoreNLP</Heading>
                        <div>CoreNLP Production - English</div>
                        <Label>Actived</Label>
                        <div className="text-gray-light d-flex flex-items-center" style={{marginTop: "10px"}}>
                            <div className="mr-3"><span
                                style={{position: "relative", top: "1px", display: "inline-block", width: "12px", height: "12px", borderRadius: "50%", backgroundColor: "#2b7489"}}></span><span
                                className="ml-2">NLP</span></div>
                        </div>
                    </div>
                    <div>

                    </div>
                </PageLayout.Content>
                <PageLayout.Pane>

                </PageLayout.Pane>
                <PageLayout.Footer>

                </PageLayout.Footer>
            </PageLayout>


        </ThemeProvider>
    )
}

export default App;
