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
import { Button, Card, Container } from "react-bootstrap";

function App() {
  return (
    <div>
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
    <Container className="p-3">
    <Card style={{ width: '18rem' }}>
  <Card.Img variant="top" src="holder.js/100px180" />
  <Card.Body>
    <Card.Title>Card Title</Card.Title>
    <Card.Text>
      Some quick example text to build on the card title and make up the bulk of
      the card's content.
    </Card.Text>
    <Button variant="primary">Go somewhere</Button>
  </Card.Body>
</Card>
    </Container>
    </div>
  );
}

export default App;
