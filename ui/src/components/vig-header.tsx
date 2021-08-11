import React from 'react';
import { TextInput, Header, Dropdown } from '@primer/components'
import { SearchIcon, PlusIcon } from '@primer/styled-octicons'

interface IProps {
}
interface IState {
  message?: String;
}
class VigHeader extends React.Component<IProps, IState> {
  constructor(props: IProps) {
    super(props);

    this.state = {
      message: ""
    }
  }

  handleMessageChanged(event?: any ) {
    this.setState({
      message: event.target.value
    });
  }

  render() {
    return (
      <Header>
        <Header.Item>
          <Header.Link href="#" fontSize={2}>
            <div style={{borderRadius: "4px", borderStyle: "solid", borderWidth: "thin", paddingRight: "5px", paddingLeft: "5px", paddingTop: "8px", paddingBottom: "1px", width: "35px", backgroundColor: "royalblue"}}>
            Tu
            </div>
          </Header.Link>
        </Header.Item>
        <Header.Item full>
          <TextInput css type="search" icon={SearchIcon} width={320} />
        </Header.Item>
        <Header.Item mr={3}>
          <Dropdown>
            <summary>
              <PlusIcon size={20}></PlusIcon> <Dropdown.Caret ml={0} />
            </summary>
            <Dropdown.Menu direction='sw'>
              <Dropdown.Item>New Repository</Dropdown.Item>
              <Dropdown.Item>Import Repository</Dropdown.Item>
              <Dropdown.Item>New Organization</Dropdown.Item>
              <Dropdown.Item>Import Organization</Dropdown.Item>
              <Dropdown.Item>New Post Type</Dropdown.Item>
              <Dropdown.Item>Import Post Type</Dropdown.Item>
            </Dropdown.Menu>
          </Dropdown>
        </Header.Item>
        <Header.Item mr={0}>
          <Dropdown>
            <summary>
              <Dropdown.Caret ml={0} />
            </summary>
            <Dropdown.Menu direction='sw'>
              <Dropdown.Item>Signed in as admin</Dropdown.Item>
              <Dropdown.Item>Sign out</Dropdown.Item>
            </Dropdown.Menu>
          </Dropdown>
        </Header.Item>
      </Header>
    );
  }
}

export default VigHeader;