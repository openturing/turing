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

interface IProps {
}

interface NLPEntity {
  name: String,
  description: String
}
interface IState {
  nlpEntities?: Array<NLPEntity>;
  message?: String;
}
class TurNLPEntityPage extends React.Component<IProps, IState> {
  constructor(props: IProps) {
    super(props);
    this.retrieveNLPEntities = this.retrieveNLPEntities.bind(this);
    this.state = {
      nlpEntities: [],
    };
  }

  handleMessageChanged(event?: any) {
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
          <Box gridTemplateColumns="repeat(2, auto)" gridGap={3}>
            <Box>
              <TextInput css
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
