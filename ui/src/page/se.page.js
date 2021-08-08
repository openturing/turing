import React from "react";
import {
  Box,
  Button,
  ButtonPrimary,
  Label,
  Pagehead,
  TextInput,Link
} from "@primer/components";
import { PackageIcon, SearchIcon, UploadIcon } from "@primer/styled-octicons";
import TurSEDataService from "../services/tur-se.service";
import VigJdenticon from "../commons/vig-jdenticon";

class TurSEPage extends React.Component {
  constructor(props) {
    super(props);
    this.retrieveSEInstances = this.retrieveSEInstances.bind(this);
    this.state = {
      searchEngines: [],
    };
  }

  handleMessageChanged(event) {
    this.setState({
      message: event.target.value,
    });
  }
  componentDidMount() {
    this.retrieveSEInstances();
  }
  retrieveSEInstances() {
    TurSEDataService.getInstances()
      .then((response) => {
        this.setState({
          searchEngines: response.data,
        });
        console.log(response.data);
      })
      .catch((e) => {
        console.log(e);
      });
  }
  render() {
    const { searchEngines } = this.state;
    return (
      <div>
        <Pagehead>
          <Box layout="grid" gridTemplateColumns="repeat(2, auto)" gridGap={3}>
            <Box>
              <TextInput
                icon={SearchIcon}
                width="50%"
                placeholder="Find a Search Engine Instance..."
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
          </Box>
        </Pagehead>
        {searchEngines &&
          searchEngines.map((seInstance, index) => (
            <Pagehead key={index} paddingTop={0}>
              <VigJdenticon size="24" value={seInstance.title.toLowerCase()} />
              <Link ml={1} href="#" fontSize={"large"} fontWeight={"bolder"}>
                {seInstance.title}
              </Link>
              <Box marginTop={"5px"}>{seInstance.description}</Box>
              <Label variant="medium" outline mt={2}>
                Enabled
              </Label>
            </Pagehead>
          ))}
      </div>
    );
  }
}

export default TurSEPage;
