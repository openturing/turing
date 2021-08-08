import { Box, Grid, SideNav, Text } from "@primer/components";
import React from "react";
import { NavLink, Redirect, Route, Switch } from "react-router-dom";
import TurNLPInstancePage from "./nlp/tur-nlp-instance.page";

function TurNLPPage() {
  return (
    <Grid gridTemplateColumns="repeat(2, auto)" gridGap={3}>
      <Box>
        <SideNav bordered maxWidth={360} aria-label="Main">
          <SideNav.Link as={NavLink} to="/nlp/instance">
            <Text>NLPs</Text>
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
          <Route path="/nlp/entity">
            <h1>BBB</h1>
          </Route>
          <Route path="/nlp/validation">
            <h1>CCC</h1>
          </Route>
        </Switch>
      </Box>
    </Grid>
  );
}

export default TurNLPPage;
