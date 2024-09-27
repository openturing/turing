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
import {Avatar, Header, UnderlineNav} from "@primer/react";
import {ReactComponent as TuringSvg} from "../logo/turing-logo.svg";
import React from "react";
import {CodeIcon, CodescanIcon, CommentDiscussionIcon, PlugIcon, SearchIcon} from "@primer/octicons-react";

export default class TurHeaderComponent extends React.Component {
    render() {
        return (<>
                <Header>
                    <Header.Item>
                        <Header.Link href="#" fontSize={2}>
                            <div style={{marginRight: "10px"}}>
                                <TuringSvg></TuringSvg>
                            </div>
                            <span>Administration Console</span>
                        </Header.Link>
                    </Header.Item>
                    <Header.Item full></Header.Item>
                    <Header.Item sx={{
                        mr: 0
                    }}>
                        <Avatar src="https://github.com/octocat.png" size={20} square alt="@octocat"/>
                    </Header.Item>
                </Header>
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
            </>
        )
    }
}
