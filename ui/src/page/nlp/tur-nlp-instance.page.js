import {
  Box,
  Button,
  ButtonPrimary,
  Grid,
  Pagehead,
  TextInput,
} from "@primer/components";
import { PackageIcon, SearchIcon, UploadIcon } from "@primer/styled-octicons";
import React from "react";

class TurNLPInstancePage extends React.Component {
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
              <TextInput
                icon={SearchIcon}
                width="50%"
                placeholder="Find a NLP..."
              />
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
        <h1>NLP Instance Page</h1>
      </div>
    );
  }
}

export default TurNLPInstancePage;
