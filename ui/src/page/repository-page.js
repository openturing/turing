import React from 'react';
import { TextInput, FilteredSearch, Dropdown, Grid, Box, ButtonPrimary, Button, Pagehead, Link, Label } from '@primer/components'
import { SearchIcon, UploadIcon, PackageIcon } from '@primer/styled-octicons'
import VigJdenticon from '../commons/vig-jdenticon';
import VigBullet from '../commons/vig-bullet'
import ShioSiteDataService from '../services/sh-sites.service'
class VigRepositoryPage extends React.Component {
  constructor(props) {
    super(props);
    this.retrieveSites = this.retrieveSites.bind(this);
    this.state = {
      sites: []
    }
  }

  handleMessageChanged(event) {
    this.setState({
      message: event.target.value
    });
  }

  componentDidMount() {
    this.retrieveSites();
  }
  retrieveSites() {
    ShioSiteDataService.getAll()
      .then(response => {
        this.setState({
          sites: response.data
        });
        console.log(response.data);
      })
      .catch(e => {
        console.log(e);
      });
  }
  render() {
    const { sites } = this.state;
    return (
      <div>
        <Pagehead>
          <Grid gridTemplateColumns="repeat(2, auto)" gridGap={3}>
            <Box>
              <FilteredSearch>
                <Dropdown>
                  <Dropdown.Button>Types</Dropdown.Button>
                  <Dropdown.Menu direction="se">
                    <Dropdown.Item>Site</Dropdown.Item>
                    <Dropdown.Item>Templates</Dropdown.Item>
                    <Dropdown.Item>Documents</Dropdown.Item>
                    <Dropdown.Item>Media</Dropdown.Item>
                  </Dropdown.Menu>
                </Dropdown>
                <TextInput icon={SearchIcon} width="50%" placeholder="Find a repository..." />
              </FilteredSearch>
            </Box>
            <Box sx={{ textAlign: 'right' }}>
              <Button mr={2}><UploadIcon /> Import</Button>
              <ButtonPrimary><PackageIcon /> New </ButtonPrimary>
            </Box>
          </Grid>
        </Pagehead>
        {sites &&
          sites.map((site, index) => (
            <Pagehead key={index} paddingTop={0}>
              <VigJdenticon size="24" value={site.name.toLowerCase()} />
              <Link ml={1} href="#" fontSize={"large"} fontWeight={"bolder"}>
                {site.name}
              </Link>
              <Box marginTop={"5px"}>
                {site.description}
              </Box>
              <Label variant="medium" outline mt={2} >Published</Label>
              <VigBullet value="Site" color="#2b7489" />
            </Pagehead>
          ))}
      </div>
    );
  }
}

export default VigRepositoryPage;