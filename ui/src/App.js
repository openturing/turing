import { Grid, ThemeProvider } from "@primer/components";
import React from "react";
import VigHeader from "./components/vig-header";
import { Redirect, Route, Switch } from "react-router-dom";
import TurMainNav from "./components/tur-main-nav";
import VigRepositoryPage from "./page/repository-page";
import TurNLPPage from "./page/nlp.page";

function App() {
  return (
    <ThemeProvider>
      <VigHeader></VigHeader>
      <Grid px={[3, 4, 5, 5]} pt={3}>
        <TurMainNav></TurMainNav>
        <Switch>
          <Route exact path="/">
            <Redirect to="/home" />
          </Route>
          <Route path="/home" exact={true} component={VigRepositoryPage} />
          <Route path="/nlp" component={TurNLPPage} />
        </Switch>
      </Grid>
    </ThemeProvider>
  );
}

export default App;
