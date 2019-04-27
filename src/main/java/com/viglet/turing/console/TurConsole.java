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
	public void run(ApplicationArguments args) throws Exception {
		if (args.getNonOptionArgs().size() > 0) {
			if (args.getNonOptionArgs().get(1).equals("encrypt")) {
				if (args.getNonOptionArgs().get(1).equals("encrypt")) {
					System.out.println(turEncryptCLI.encrypt(args.getOptionValues("input").get(0)));
				}
			}
		}
	}
}
