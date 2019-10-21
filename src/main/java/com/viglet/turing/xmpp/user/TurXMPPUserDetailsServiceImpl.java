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

package com.viglet.turing.xmpp.user;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.converse.xmpp.TurXMPPUser;

import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;

/**
 * @author Yuriy Tumakha.
 */
@Service("userDetailsService")
public class TurXMPPUserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private TurXMPPUserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    	TurXMPPUser user = userService.findByUsername(username);
        if (user == null || !user.isAdmin()) {
            throw new UsernameNotFoundException(String.format("User '%s' with role 'ADMIN' was not found", username));
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPassword(), createAuthorityList("ADMIN"));
    }

}
