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

package com.viglet.turing.onstartup.xmpp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.converse.xmpp.TurXMPPUser;
import com.viglet.turing.persistence.repository.converse.xmpp.TurXMPPUserRepository;
import com.viglet.turing.xmpp.user.TurXMPPUserService;

@Component
@Transactional
public class TurXMPPStartup {

	@Autowired
	private  TurXMPPUserRepository turXMPPUserRepository;
    @Autowired
    private TurXMPPUserService turXMPPUserService;
    
	public void createDefaultRows() {

		if (turXMPPUserRepository.findAll().isEmpty()) {
			TurXMPPUser turXMPPUser = new TurXMPPUser();
			
			turXMPPUser.setId((long) 1);
			turXMPPUser.setUsername("admin");
			turXMPPUser.setPassword("admin");
			turXMPPUser.setAdmin(true);
			turXMPPUserService.createUser(turXMPPUser);
			
			turXMPPUser = new TurXMPPUser();
			
			turXMPPUser.setId((long) 1);
			turXMPPUser.setUsername("user1");
			turXMPPUser.setPassword("user1");
			turXMPPUser.setAdmin(false);
			turXMPPUserService.createUser(turXMPPUser);
		}
	}
}
