/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import React, { Component } from 'react'
import PropTypes from 'prop-types'

class Input extends Component {

    constructor(props){
        super(props)
        this.state = {
            value: props.value? props.value : '',
            className: props.className? props.className : '',
            error: false
        }
    }

    inputChange = (event) => {
        const value = event.target.value, name = event.target.name
        //switch(name) {
        //  case 'username': this.validate(name, value, /^([a-zA-Z0-9.]{4,})$/, 'invalid username')
        //    break;
        //  case 'password': this.validate(name, value, /^(?=.*[A-Z])(?=.*[0-9])(?=.*[a-z]).{6,}$/, 'insecure password')
        //    break;
        //  default:
        //    console.warn(`unknown field ${name}`)
        //}
        this.setState({ value: value })
    }

    validate = (name, value, validRegex, warnmsg) => {
        const invalid = !value || !validRegex.test(value)
        if(!this.state.error && invalid) {
            this.setState({ className: 'input-error', error: true })
            this.handleError(name, warnmsg)
        }else if(this.state.error && !invalid) {
            this.setState({ className: '', error: false })
            this.handleError(name)
        }
    }

    render (){
        const {handleError, ...opts} = this.props
        this.handleError = handleError
        return (
            <input {...opts} value={this.state.value} onChange={this.inputChange} className={this.state.className} />
        )
    }
}

Input.propTypes = {
    name: PropTypes.string,
    placeholder: PropTypes.string,
    type: PropTypes.string,
    className: PropTypes.string,
    handleError: PropTypes.func,
    value: PropTypes.string
}

export default Input

