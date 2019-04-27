package com.viglet.turing.console.encrypt;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.stereotype.Component;

@Component
public class TurEncryptCLI {

	 @Qualifier("jasyptStringEncryptor")
	 @Autowired StringEncryptor stringEncryptor;
	 
	public String encrypt(String input) {
		String result = null;
        try {
        	          
        	PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
    		SimpleStringPBEConfig config = new SimpleStringPBEConfig();
    		config.setPassword("password");
    		config.setAlgorithm("PBEWithMD5AndDES");
    		config.setKeyObtentionIterations("1000");
    		config.setPoolSize("1");
    		config.setProviderName("SunJCE");
    		config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
    		config.setIvGeneratorClassName("org.jasypt.salt.NoOpIVGenerator");
    		config.setStringOutputType("base64");
    		encryptor.setConfig(config);
        	        
    		//String encrypt = encryptor.encrypt(input);
    		String encrypt = stringEncryptor.encrypt(input);
        	        return  encrypt + " " +  stringEncryptor.decrypt(encrypt);

      
        } catch (Throwable t) {
           t.printStackTrace();
        }
        return result;
    }
    
   
    
}