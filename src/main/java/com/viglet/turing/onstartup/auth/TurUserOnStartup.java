/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as publitured by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You turould have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.onstartup.auth;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

@Component
public class TurUserOnStartup {
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private TurUserRepository turUserRepository;
	@Autowired
	private TurGroupRepository turGroupRepository;
	
	public void createDefaultRows() {

		if (turUserRepository.findAll().isEmpty()) {
			
			TurGroup turGroup = turGroupRepository.findByName("Administrator");
			
			Set<TurGroup> turGroups = new HashSet<>();
			turGroups.add(turGroup);

			TurUser turUser = new TurUser();

			turUser.setEmail("admin@localhost.local");
			turUser.setFirstName("Admin");
			turUser.setLastLogin(new Date());
			turUser.setLastName("Administrator");
			turUser.setLoginTimes(0);
			turUser.setPassword(passwordEncoder.encode("admin"));
			turUser.setRealm("default");
			turUser.setUsername("admin");
			turUser.setEnabled(1);

			turUser.setTurGroups(turGroups);
			
			turUserRepository.save(turUser);			
						
			
			turUser = new TurUser();

			turUser.setEmail("sample@localhost.local");
			turUser.setFirstName("Sample user");
			turUser.setLastLogin(new Date());
			turUser.setLastName("Sample");
			turUser.setLoginTimes(0);
			turUser.setPassword(passwordEncoder.encode("sample123"));
			turUser.setRealm("default");
			turUser.setUsername("sample");
			turUser.setEnabled(1);
			
			turUserRepository.save(turUser);			
		}

	}
}
