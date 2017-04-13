/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.auth.google;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.PeopleFeed;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Simple server to demonstrate how to use Google+ Sign-In and make a request
 * via your own server.
 *
 * @author joannasmith@google.com (Joanna Smith)
 * @author vicfryzel@google.com (Vic Fryzel)
 */
public class SigninServlet extends HttpServlet {
	/*
	 * Default HTTP transport to use to make HTTP requests.
	 */
	public static final HttpTransport TRANSPORT = new NetHttpTransport();

	/*
	 * Default JSON factory to use to deserialize JSON.
	 */
	public static final JacksonFactory JSON_FACTORY = new JacksonFactory();

	/*
	 * Gson object to serialize JSON responses to requests to this servlet.
	 */
	public static final Gson GSON = new Gson();

	/*
	 * Creates a client secrets object from the client_secrets.json file.
	 */
	public static GoogleClientSecrets clientSecrets;

	static {
		try {
			// Reader reader = new
			// FileReader("/Applications/MAMP/htdocs/semantics-web/tomcat/webapps/semantics/client_secrets.json");
			Class cls;

			cls = Class.forName("com.viglet.turing.auth.google.SigninServlet");

			// returns the ClassLoader object associated with this Class
			ClassLoader cLoader = cls.getClassLoader();
			// input stream
			InputStream is = cLoader.getResourceAsStream("client_secrets.json");
			Reader reader = new InputStreamReader(is);
			clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
		} catch (IOException e) {
			throw new Error("No client_secrets.json found", e);
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * This is the Client ID that you generated in the API Console.
	 */
	public static final String CLIENT_ID = clientSecrets.getWeb().getClientId();

	/*
	 * This is the Client Secret that you generated in the API Console.
	 */
	public static final String CLIENT_SECRET = clientSecrets.getWeb().getClientSecret();

	/*
	 * Optionally replace this with your application's name.
	 */
	public static final String APPLICATION_NAME = "Google+ Java Quickstart";

}
