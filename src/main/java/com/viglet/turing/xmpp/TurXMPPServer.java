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

package com.viglet.turing.xmpp;

import org.apache.vysper.mina.S2SEndpoint;
import org.apache.vysper.mina.TCPEndpoint;
import org.apache.vysper.storage.StorageProviderRegistry;
import org.apache.vysper.storage.inmemory.MemoryStorageProviderRegistry;
import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.authorization.AccountManagement;
import org.apache.vysper.xmpp.authorization.Anonymous;
import org.apache.vysper.xmpp.authorization.SASLMechanism;

import org.apache.vysper.xmpp.modules.extension.xep0045_muc.MUCModule;
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.model.Conference;
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.model.RoomType;
import org.apache.vysper.xmpp.modules.extension.xep0054_vcardtemp.VcardTempModule;

import org.apache.vysper.xmpp.modules.extension.xep0060_pubsub.PublishSubscribeModule;

import org.apache.vysper.xmpp.modules.extension.xep0077_inbandreg.InBandRegistrationModule;
import org.apache.vysper.xmpp.modules.extension.xep0092_software_version.SoftwareVersionModule;
import org.apache.vysper.xmpp.modules.extension.xep0119_xmppping.XmppPingModule;
import org.apache.vysper.xmpp.modules.extension.xep0202_entity_time.EntityTimeModule;
import org.apache.vysper.xmpp.protocol.DefaultHandlerDictionary;
import org.apache.vysper.xmpp.protocol.HandlerDictionary;
import org.apache.vysper.xmpp.server.DefaultServerRuntimeContext;
import org.apache.vysper.xmpp.server.ServerFeatures;
import org.apache.vysper.xmpp.server.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.viglet.turing.xmpp.message.TurXMPPMessageHandler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

@Component
public class TurXMPPServer implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(TurXMPPServer.class);

	@Autowired
	private Environment env;

	@Autowired
	private StorageProviderRegistry storageProviderRegistry;

	@Autowired
	private TurXMPPMessageHandler turXMPPMessageHandler;
	@Autowired
	private ResourceLoader resourceloader;

	private XMPPServer xmppServer;

	private String domain;
	private int xmppPort;
	private String keystore;
	private String keystorePassword;
	private boolean saveMessage;

	@PostConstruct
	public void init() {
		domain = env.getProperty("xmpp.domain");
		xmppPort = env.getProperty("xmpp.clients.port", Integer.class, 5222);
		keystore = env.getProperty("xmpp.keystore.path");
		keystorePassword = env.getProperty("xmpp.keystore.password");
		saveMessage = env.getProperty("xmpp.message.save", Boolean.class, false);
	}

	@Override
	public void run(String... args) throws Exception {

		xmppServer = new XMPPServer(domain);
		
		xmppServer.setStorageProviderRegistry(storageProviderRegistry);
		final TCPEndpoint endpoint = new TCPEndpoint();
		endpoint.setPort(xmppPort);
		xmppServer.addEndpoint(endpoint);
		// allow XMPP federation
		xmppServer.addEndpoint(new S2SEndpoint());

		InputStream is = resourceloader.getResource("classpath:/bogus_mina_tls.cert").getInputStream();
		xmppServer.setTLSCertificateInfo(is, "boguspw");

		// allow anonymous authentication
		xmppServer.setSASLMechanisms(Arrays.asList(new SASLMechanism[] { new Anonymous() }));

		xmppServer.start();

		// add the multi-user chat module and create a room
		Conference conference = new Conference("Conference");
		conference.createRoom(EntityImpl.parseUnchecked("public@" + domain), "Public Room", RoomType.Public);

		final ServerFeatures serverFeatures = xmppServer.getServerRuntimeContext().getServerFeatures();
		serverFeatures.setRelayingToFederationServers(true);

		xmppServer.addModule(new MUCModule("conference", conference));
		xmppServer.addModule(new InBandRegistrationModule());
		xmppServer.addModule(new XmppPingModule());
		xmppServer.addModule(new PublishSubscribeModule());
		xmppServer.addModule(new SoftwareVersionModule());
		xmppServer.addModule(new EntityTimeModule());
		xmppServer.addModule(new VcardTempModule());

		if (saveMessage) {
			// add MessageHandler
			HandlerDictionary handlerDictionary = new DefaultHandlerDictionary(turXMPPMessageHandler);
			((DefaultServerRuntimeContext) xmppServer.getServerRuntimeContext()).addDictionary(handlerDictionary);
		}

		LOG.info("XMPP Server is running on port {}", xmppPort);
	}

	@PreDestroy
	public void shutdown() {
		xmppServer.stop();
	}

}
