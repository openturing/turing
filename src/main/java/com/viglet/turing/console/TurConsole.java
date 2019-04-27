package com.viglet.turing.console;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.stereotype.Component;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import com.viglet.turing.console.encrypt.TurEncryptCLI;

@Component
@ComponentScan(basePackages = {"com.viglet.turing.console.encrypt", "com.viglet.turing.encrypt"})
public class TurConsole implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(TurConsole.class);

	@Autowired
	private TurEncryptCLI turEncryptCLI;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		logger.info("Application started with command-line arguments: {}", Arrays.toString(args.getSourceArgs()));
		logger.info("NonOptionArgs: {}", args.getNonOptionArgs());
		logger.info("OptionNames: {}", args.getOptionNames());

		if (args.getNonOptionArgs().size() > 0) {
			if (args.getNonOptionArgs().get(1).equals("encrypt")) {
				System.out.println("Encrypt mode: " + turEncryptCLI.encrypt("teste123"));
			}
		}
		for (String name : args.getOptionNames()) {
			logger.info("arg-" + name + "=" + args.getOptionValues(name));
		}

		boolean containsOption = args.containsOption("person.name");
		logger.info("Contains person.name: " + containsOption);

	}
}
