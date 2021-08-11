import {
  Box,
  ButtonDanger,
  ButtonPrimary,
  FormGroup,
  Pagehead,
  Text,
  TextInput
} from "@primer/components";
import React from "react";
import { RouteComponentProps } from "react-router-dom";

type Props = {};
type ComposedProps = Props & RouteComponentProps<{
  id: string;
}>;

interface IState {
  message?: String;
}
class TurSEInstancePage extends React.Component<ComposedProps, IState> {
  constructor(props: ComposedProps) {
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
      <Box
        style={{ maxWidth: "1012px", marginLeft: "auto", marginRight: "auto" }}
      >
        <Pagehead>
          <h2>SE Instance {this.props.match.params.id}</h2>
        </Pagehead>
        <textarea></textarea>
        <Text fontSize={[1, 1, 1, 4]} />
        <FormGroup>
          <FormGroup.Label htmlFor="se-instance-name">Name</FormGroup.Label>
          <TextInput css id="se-instance-name" width="100%" />
        </FormGroup>
        <FormGroup>
          <FormGroup.Label htmlFor="se-instance-description">
            Description
          </FormGroup.Label>
          <TextInput css id="se-instance-description" width="100%" />
        </FormGroup>
        <FormGroup>
          <FormGroup.Label htmlFor="se-instance-vendors">
            Vendors
          </FormGroup.Label>
          <select className="form-select" value="Radish">
            <option value="Orange">Orange</option>
            <option value="Radish">Radish</option>
            <option value="Cherry">Cherry</option>
          </select>
        </FormGroup>
        <FormGroup>
          <FormGroup.Label htmlFor="se-instance-host">Host</FormGroup.Label>
          <TextInput css id="se-instance-host" width="100%" />
        </FormGroup>
        <FormGroup>
          <FormGroup.Label htmlFor="se-instance-port">Port</FormGroup.Label>
          <TextInput css id="se-instance-port" width="100%" />
        </FormGroup>
        <FormGroup>
          <FormGroup.Label htmlFor="se-instance-language">
            Language
          </FormGroup.Label>
          <TextInput css id="se-instance-language" width="100%" />
        </FormGroup>
        <FormGroup>
          <FormGroup.Label htmlFor="se-instance-enabled">
            Enabled
          </FormGroup.Label>
          <TextInput css id="se-instance-enabled" width="100%" />
        </FormGroup>

        <ButtonPrimary mr={2}>Save Changes</ButtonPrimary>

        <ButtonDanger>Delete</ButtonDanger>
      </Box>
    );
  }
}

export default TurSEInstancePage;
