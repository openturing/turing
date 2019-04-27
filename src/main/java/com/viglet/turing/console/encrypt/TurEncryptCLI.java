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