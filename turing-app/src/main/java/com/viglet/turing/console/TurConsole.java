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

package com.viglet.turing.console;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import com.viglet.turing.console.encrypt.TurEncryptCLI;

@Component
@ComponentScan(basePackages = { "com.viglet.turing.console.encrypt", "com.viglet.turing.encrypt" })
public class TurConsole implements ApplicationRunner {
	@Autowired
	private TurEncryptCLI turEncryptCLI;

	@Override
	public void run(ApplicationArguments args) {
		if (!args.getNonOptionArgs().isEmpty() && args.getNonOptionArgs().get(1).equals("encrypt")) {
			System.console().writer().println(turEncryptCLI.encrypt(args.getOptionValues("input").get(0)));
		}
	}
}
