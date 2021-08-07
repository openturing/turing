import React from 'react';
import { UnderlineNav, CounterLabel, Grid } from '@primer/components'
import { RepoIcon, WorkflowIcon, PackageIcon, CodeIcon } from '@primer/styled-octicons'
import VigRepositoryPage from './repository-page';

class DashboardPage extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      message: ""
    }
  }

  handleMessageChanged(event) {
    this.setState({
      message: event.target.value
    });
  }

  render() {
    return (
      <Grid  px={[3, 4, 5, 5]} pt={3}>
        <UnderlineNav aria-label="Main" mb={4} >
          <UnderlineNav.Link href="#home" selected><RepoIcon verticalAlign="middle" mr={1} /> Repository <CounterLabel>2</CounterLabel></UnderlineNav.Link>
          <UnderlineNav.Link href="#documentation"><WorkflowIcon verticalAlign="middle" mr={1} /> Tasks <CounterLabel>10</CounterLabel></UnderlineNav.Link>
          <UnderlineNav.Link href="#support"><PackageIcon verticalAlign="middle" mr={1} /> Modeling</UnderlineNav.Link>
          <UnderlineNav.Link href="#support"><CodeIcon verticalAlign="middle" mr={1} /> API Playground</UnderlineNav.Link>
        </UnderlineNav>
        <VigRepositoryPage></VigRepositoryPage>
      </Grid>
    );
  }
}

export default DashboardPage;