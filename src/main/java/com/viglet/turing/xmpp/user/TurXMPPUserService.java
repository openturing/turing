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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.converse.xmpp.TurXMPPUser;


@Transactional(readOnly = true)
public interface TurXMPPUserService {

    @Modifying
    @Transactional
    void createUser(TurXMPPUser user);

    @Modifying
    @Transactional
    void updateUser(TurXMPPUser user);

    @Modifying
    @Transactional
    void changePassword(String username, String newPassword);

    @Modifying
    @Transactional
    void removeUser(Long userId);

    TurXMPPUser findById(Long userId);

    TurXMPPUser findByUsername(String username);

    boolean verifyCredentials(String username, String passwordCleartext);

    Page<TurXMPPUser> findAll(Pageable pageable);

}
