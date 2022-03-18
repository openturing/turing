/*
 * Copyright (C) 2021 the original author or authors. 
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

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.stereotype.Component;

@Component
public class TurEncryptCLI {
	private static final Log logger = LogFactory.getLog(MethodHandles.lookup().lookupClass());
	@Qualifier("turEncryptor")
	@Autowired
	private StringEncryptor stringEncryptor;

	public String encrypt(String input) {
		try {
			return stringEncryptor.encrypt(input);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

}