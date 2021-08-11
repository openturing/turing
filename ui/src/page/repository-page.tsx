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

interface IProps {
  name: String,
  description: String
}
interface IState {
  message?: String;
}

class VigRepositoryPage extends React.Component<IProps, IState> {
  constructor(props: IProps) {
    super(props);
    this.state = {
      message: "",
    };
  }

  handleMessageChanged(event?: any) {
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
                <TextInput css
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
