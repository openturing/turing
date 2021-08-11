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
import TurChatbotDataService from "../services/tur-chatbot.service";

interface ChatbotAgent {
  name: String,
  description: String
}
interface IProps {
  name: String,
  description: String
}
interface IState {
  chatbotAgents?: Array<ChatbotAgent>;
  message?: String;
}

class TurChatbotPage extends React.Component<IProps, IState> {
  constructor(props: IProps) {
    super(props);
    this.retrieveChatbotAgents = this.retrieveChatbotAgents.bind(this);
    this.state = {
      chatbotAgents: [],
    };
  }

  handleMessageChanged(event?: any) {
    this.setState({
      message: event.target.value,
    });
  }
  componentDidMount() {
    this.retrieveChatbotAgents();
  }
  retrieveChatbotAgents() {
    TurChatbotDataService.getAgents()
      .then((response) => {
        this.setState({
          chatbotAgents: response.data,
        });
        console.log(response.data);
      })
      .catch((e) => {
        console.log(e);
      });
  }
  render() {
    const { chatbotAgents } = this.state;
    return (
      <div>
        <Pagehead>
          <Box gridTemplateColumns="repeat(2, auto)" gridGap={3}>
            <Box>
              <TextInput css
                icon={SearchIcon}
                width="50%"
                placeholder="Find a Chatbot Agent..."
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
        {chatbotAgents &&
          chatbotAgents.map((chatbotAgent, index) => (
            <Pagehead key={index} paddingTop={0}>
              <Link ml={1} href="#" fontSize={"large"} fontWeight={"bolder"}>
                {chatbotAgent.name}
              </Link>
              <Box marginTop={"5px"}>{chatbotAgent.description}</Box>
              <Label variant="medium" outline mt={2}>
                Enabled
              </Label>
            </Pagehead>
          ))}
      </div>
    );
  }
}

export default TurChatbotPage;
