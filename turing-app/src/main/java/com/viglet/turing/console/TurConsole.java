/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.console;

import com.google.inject.Inject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import com.viglet.turing.console.encrypt.TurEncryptCLI;

@Component
@ComponentScan(basePackages = { "com.viglet.turing.console.encrypt", "com.viglet.turing.encrypt" })
public class TurConsole implements ApplicationRunner {
	private final TurEncryptCLI turEncryptCLI;

	@Inject
	public TurConsole(TurEncryptCLI turEncryptCLI) {
		this.turEncryptCLI = turEncryptCLI;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!args.getNonOptionArgs().isEmpty() && args.getNonOptionArgs().get(1).equals("encrypt")) {
			System.err.println(turEncryptCLI.encrypt(args.getOptionValues("input").get(0)));
		}
	}
}
