/*
 * Copyright (C) 2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */ 

package com.viglet.turing.console.encrypt;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.stereotype.Component;

@Component
public class TurEncryptCLI {

	@Qualifier("turEncryptor")
	@Autowired
	StringEncryptor stringEncryptor;

	public String encrypt(String input) {
		String result = null;
		try {
			return stringEncryptor.encrypt(input);

		} catch (Throwable t) {
			t.printStackTrace();
		}
		return result;
	}

}