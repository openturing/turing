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
import TurNLPDataService from "../../services/tur-nlp.service";
import VigJdenticon from "../../commons/vig-jdenticon";

class TurNLPEntityPage extends React.Component {
  constructor(props) {
    super(props);
    this.retrieveNLPEntities = this.retrieveNLPEntities.bind(this);
    this.state = {
      nlpEntities: [],
    };
  }

  handleMessageChanged(event) {
    this.setState({
      message: event.target.value,
    });
  }
  componentDidMount() {
    this.retrieveNLPEntities();
  }
  retrieveNLPEntities() {
    TurNLPDataService.getEntities()
      .then((response) => {
        this.setState({
          nlpEntities: response.data,
        });
        console.log(response.data);
      })
      .catch((e) => {
        console.log(e);
      });
  }
  render() {
    const { nlpEntities } = this.state;
    return (
      <div>
        <Pagehead>
          <Box layout="grid" gridTemplateColumns="repeat(2, auto)" gridGap={3}>
            <Box>
              <TextInput
                icon={SearchIcon}
                width="50%"
                placeholder="Find a NLP Entity..."
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
        {nlpEntities &&
          nlpEntities.map((nlpEntity, index) => (
            <Pagehead key={index} paddingTop={0}>
              <VigJdenticon size="24" value={nlpEntity.name.toLowerCase()} />
              <Link ml={1} href="#" fontSize={"large"} fontWeight={"bolder"}>
                {nlpEntity.name}
              </Link>
              <Box marginTop={"5px"}>{nlpEntity.description}</Box>
              <Label variant="medium" outline mt={2}>
                Enabled
              </Label>
            </Pagehead>
          ))}
      </div>
    );
  }
}

export default TurNLPEntityPage;
