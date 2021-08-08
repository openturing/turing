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
import TurSNDataService from "../services/tur-sn.service";
import VigJdenticon from "../commons/vig-jdenticon";

class TurSNPage extends React.Component {
  constructor(props) {
    super(props);
    this.retrieveSNSites = this.retrieveSNSites.bind(this);
    this.state = {
      snSites: [],
    };
  }

  handleMessageChanged(event) {
    this.setState({
      message: event.target.value,
    });
  }
  componentDidMount() {
    this.retrieveSNSites();
  }
  retrieveSNSites() {
    TurSNDataService.getSites()
      .then((response) => {
        this.setState({
          snSites: response.data,
        });
        console.log(response.data);
      })
      .catch((e) => {
        console.log(e);
      });
  }
  render() {
    const { snSites } = this.state;
    return (
      <div>
        <Pagehead>
          <Box layout="grid" gridTemplateColumns="repeat(2, auto)" gridGap={3}>
            <Box>
              <TextInput
                icon={SearchIcon}
                width="50%"
                placeholder="Find a Semantic Navigation Site..."
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
        {snSites &&
          snSites.map((snSite, index) => (
            <Pagehead key={index} paddingTop={0}>
              <VigJdenticon size="24" value={snSite.name.toLowerCase()} />
              <Link ml={1} href="#" fontSize={"large"} fontWeight={"bolder"}>
                {snSite.name}
              </Link>
              <Box marginTop={"5px"}>{snSite.description}</Box>
              <Label variant="medium" outline mt={2}>
                Enabled
              </Label>
            </Pagehead>
          ))}
      </div>
    );
  }
}

export default TurSNPage;
