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

package com.viglet.turing.spring.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

@Service("customUserDetailsService")
public class TurCustomUserDetailsService implements UserDetailsService {
	@Autowired
	private TurUserRepository turUserRepository;
	@Autowired
	private TurRoleRepository turRoleRepository;
	@Autowired
	private TurGroupRepository turGroupRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		TurUser turUser = turUserRepository.findByUsername(username);
		if (null == turUser) {
			throw new UsernameNotFoundException("No user present with username: " + username);
		} else {
			List<TurUser> users = new ArrayList<>();
			users.add(turUser);
			Set<TurGroup> turGroups = turGroupRepository.findByTurUsersIn(users);
			Set<TurRole> turRoles = turRoleRepository.findByTurGroupsIn(turGroups);

			List<String> roles = new ArrayList<>();
			for (TurRole turRole : turRoles) {
				roles.add(turRole.getName());
			}
			return new TurCustomUserDetails(turUser, roles);
		}
	}

}
