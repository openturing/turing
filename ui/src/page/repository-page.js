import React from "react";
import {
  TextInput,
  FilteredSearch,
  Dropdown,
  Grid,
  Box,
  ButtonPrimary,
  Button,
  Pagehead
} from "@primer/components";
import { SearchIcon, UploadIcon, PackageIcon } from "@primer/styled-octicons";

class VigRepositoryPage extends React.Component {
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
                <TextInput
                  icon={SearchIcon}
                  width="50%"
                  placeholder="Find a repository..."
                />
              </FilteredSearch>
            </Box>
            <Box sx={{ textAlign: "right" }}>
              <Button mr={2}>
                <UploadIcon /> Import
              </Button>
              <ButtonPrimary>
                <PackageIcon /> New{" "}
              </ButtonPrimary>
            </Box>
          </Grid>
        </Pagehead>
      </div>
    );
  }
}

export default VigRepositoryPage;
