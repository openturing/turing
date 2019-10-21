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

package com.viglet.turing.xmpp.storage;


import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityFormatException;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.authorization.AccountCreationException;
import org.apache.vysper.xmpp.authorization.AccountManagement;
import org.apache.vysper.xmpp.authorization.UserAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.converse.xmpp.TurXMPPUser;
import com.viglet.turing.xmpp.user.TurXMPPUserService;

@Component
public class TurXMPPDbUserManagement implements UserAuthorization, AccountManagement {

    private static final Logger LOG = LoggerFactory.getLogger(TurXMPPDbUserManagement.class);

    @Autowired
    private TurXMPPUserService userService;

    @Override
    public void addUser(Entity username, String password) throws AccountCreationException {
    	TurXMPPUser user = new TurXMPPUser();
        user.setUsername(username.getNode());
        user.setPassword(password);
        userService.createUser(user);
    }

    @Override
    public void changePassword(Entity username, String password) throws AccountCreationException {
        userService.changePassword(username.getNode(), password);
    }

    @Override
    public boolean verifyAccountExists(Entity jid) {
        return userService.findByUsername(jid.getNode()) != null;
    }

    @Override
    public boolean verifyCredentials(Entity jid, String passwordCleartext, Object credentials) {
        return userService.verifyCredentials(jid.getNode(), passwordCleartext);
    }

    @Override
    public boolean verifyCredentials(String username, String passwordCleartext, Object credentials) {
        try {
            Entity jid = EntityImpl.parse(username);
            return verifyCredentials(jid, passwordCleartext, credentials);
        } catch (EntityFormatException e) {
            LOG.error("Parse JID failed", e);
        }
        return false;
    }

}
