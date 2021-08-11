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

interface NLPInstance {
  title: String,
  description: String
}
interface IProps {
  name: String,
  description: String
}
interface IState {
  nlps?: Array<NLPInstance>;
  message?: String;
} 
class TurNLPInstancePage extends React.Component<IProps, IState> {
  constructor(props: IProps) {
    super(props);
    this.retrieveNLP.bind(this);
    this.state = {
      nlps: [],
    };
  }

  handleMessageChanged(event?: any) {
    this.setState({
      message: event.target.value,
    });
  }
  componentDidMount() {
    this.retrieveNLP();
  }
  retrieveNLP() {
    TurNLPDataService.getInstances()
      .then((response) => {
        this.setState({
          nlps: response.data,
        });
        console.log(response.data);
      })
      .catch((e) => {
        console.log(e);
      });
  }
  render() {
    const { nlps } = this.state;
    return (
      <div>
        <Pagehead>
          <Box gridTemplateColumns="repeat(2, auto)" gridGap={3}>
            <Box>
              <TextInput css
                icon={SearchIcon}
                width="50%"
                placeholder="Find a NLP Instance..."
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
        {nlps &&
          nlps.map((nlp, index) => (
            <Pagehead key={index} paddingTop={0}>
              <Link ml={1} href="#" fontSize={"large"} fontWeight={"bolder"}>
                {nlp.title}
              </Link>
              <Box marginTop={"5px"}>{nlp.description}</Box>
              <Label variant="medium" outline mt={2}>
                Enabled
              </Label>
            </Pagehead>
          ))}
      </div>
    );
  }
}

export default TurNLPInstancePage;
