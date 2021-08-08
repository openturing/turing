import React from "react";

class TurSEInstancePage extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      message:""
    };
  }

  handleMessageChanged(event) {
    this.setState({
      message: event.target.value,
    });
  }
 
  render() {
    return <h1>SE Instance {this.props.match.params.id}</h1>;
  }
}

export default TurSEInstancePage;
