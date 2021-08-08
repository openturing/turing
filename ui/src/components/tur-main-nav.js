import React from "react";
import { UnderlineNav, CounterLabel } from "@primer/components";
import {
  RepoIcon,
  SearchIcon,
  CommentDiscussionIcon,
  HubotIcon,
  CodescanIcon,
} from "@primer/styled-octicons";
import { NavLink } from "react-router-dom";

class TurMainNav extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      message: "",
    };
  }

  handleMessageChanged(event) {
    this.setState({
      message: event.target.value,
    });
  }

  render() {
    return (
      <div>
        <UnderlineNav aria-label="Main" mb={4}>
          <UnderlineNav.Link as={NavLink} to="/home">
            <RepoIcon verticalAlign="middle" mr={1} /> Repository{" "}
            <CounterLabel>2</CounterLabel>
          </UnderlineNav.Link>
          <UnderlineNav.Link as={NavLink} to="/nlp">
            <CommentDiscussionIcon verticalAlign="middle" mr={1} /> NLP{" "}
            <CounterLabel>10</CounterLabel>
          </UnderlineNav.Link>
          <UnderlineNav.Link as={NavLink} to="/se">
            <CodescanIcon verticalAlign="middle" mr={1} /> Search Engine
          </UnderlineNav.Link>
          <UnderlineNav.Link as={NavLink} to="/chatbot">
            <HubotIcon verticalAlign="middle" mr={1} /> Chatbot
          </UnderlineNav.Link>
          <UnderlineNav.Link as={NavLink} to="/es">
            <SearchIcon verticalAlign="middle" mr={1} /> Enterprise Search
          </UnderlineNav.Link>
        </UnderlineNav>
      </div>
    );
  }
}

export default TurMainNav;
