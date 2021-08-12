import React from 'react';
import { TextInput, Header, Dropdown } from '@primer/components'
import { SearchIcon, PlusIcon } from '@primer/styled-octicons'
import { Button, Container, Form, FormControl, Nav, Navbar, NavDropdown } from 'react-bootstrap';

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

  handleMessageChanged(event?: any) {
    this.setState({
      message: event.target.value
    });
  }

  render() {
    return (
      <Navbar collapseOnSelect expand="lg" bg="dark" variant="dark" >
        <Container>
          <Navbar.Brand href="/">
            <div style={{ borderRadius: "4px", borderStyle: "solid", borderWidth: "thin", paddingRight: "5px", paddingLeft: "5px", paddingTop: "8px", paddingBottom: "1px", width: "41px", backgroundColor: "royalblue" }}>
              Tu
            </div>
          </Navbar.Brand>
          <Navbar.Toggle aria-controls="responsive-navbar-nav" />
          <Navbar.Collapse id="responsive-navbar-nav">
            <Nav className="me-auto">
              <Form className="d-flex">
                <FormControl
                  type="search"
                  placeholder="Search"
                  className="mr-2"
                  aria-label="Search"
                />
              </Form>
            </Nav>
            <Nav>
              <NavDropdown title="+" id="navbarScrollingDropdown">
                <NavDropdown.Item href="#action3">New Repository</NavDropdown.Item>
                <NavDropdown.Item href="#action3">Import Repository</NavDropdown.Item>
                <NavDropdown.Divider />
                <NavDropdown.Item href="#action5">New Organization</NavDropdown.Item>
              </NavDropdown>
              <NavDropdown title="admin" id="navbarScrollingDropdown">
                <NavDropdown.Item href="#action3">Signed in as admin</NavDropdown.Item>
                <NavDropdown.Divider />
                <NavDropdown.Item href="#action5">Sign out</NavDropdown.Item>
              </NavDropdown>
            </Nav>
          </Navbar.Collapse>
        </Container>
      </Navbar>
    );
  }
}

export default VigHeader;