import { BaseStyles, Box, ThemeProvider } from "@primer/components";
import VigHeader from "./components/vig-header";
import { Redirect, Route, Switch } from "react-router-dom";
import TurMainNav from "./components/tur-main-nav";
import VigRepositoryPage from "./page/repository-page";
import TurNLPPage from "./page/nlp.page";
import TurSEPage from "./page/se.page";
import TurChatbotPage from "./page/chatbot.page";
import TurSNPage from "./page/sn.page";
import TurSEInstancePage from "./page/se/se-instance.page";

function App() {
  return (
    <ThemeProvider>
      <BaseStyles>
        <VigHeader></VigHeader>
        <Box px={[3, 4, 5, 5]} pt={3}>
          <TurMainNav></TurMainNav>
            <Switch>
              <Route exact path="/">
                <Redirect to="/home" />
              </Route>
              <Route exact path="/home" component={VigRepositoryPage} />
              <Route path="/nlp" component={TurNLPPage} />
              <Route exact path="/se" component={TurSEPage} />
              <Route exact path="/se/instance/:id" component={TurSEInstancePage} />
              <Route exact path="/chatbot" component={TurChatbotPage} />
              <Route exact path="/sn" component={TurSNPage} />
            </Switch>
        </Box>
      </BaseStyles>
    </ThemeProvider>
  );
}

export default App;
