import { Box, SideNav, Text } from "@primer/components";
import React from "react";
import { NavLink, Redirect, Route, Switch } from "react-router-dom";
import TurNLPInstancePage from "./nlp/tur-nlp-instance.page";
import TurNLPEntityPage from "./nlp/tur-nlp-entity.page";

function TurNLPPage() {
  return (
    <Box display="grid" gridTemplateColumns="3fr 9fr" gridGap={0}>
      <Box>
        <SideNav bordered maxWidth={392} aria-label="Main">
          <SideNav.Link as={NavLink} to="/nlp/instance">
            <Text>Instances</Text>
          </SideNav.Link>
          <SideNav.Link as={NavLink} to="/nlp/entity">
            <Text>Entities</Text>
          </SideNav.Link>
          <SideNav.Link as={NavLink} to="/nlp/validation">
            <Text>NLP Validation</Text>
          </SideNav.Link>
        </SideNav>
      </Box>
      <Box>
        <Switch>
          <Route exact path="/nlp/">
            <Redirect to="/nlp/instance" />
          </Route>
          <Route exact path="/nlp/instance" component={TurNLPInstancePage} />
          <Route exact path="/nlp/entity" component={TurNLPEntityPage} />
 
          <Route path="/nlp/validation">
            <h1>NLP Validation</h1>
          </Route>
        </Switch>
      </Box>
    </Box>
  );
}

export default TurNLPPage;
