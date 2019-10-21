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

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.converse.xmpp.TurXMPPUser;
import com.viglet.turing.persistence.repository.converse.xmpp.TurXMPPUserRepository;

@Service("userService")
public class TurXMPPUserServiceImpl implements TurXMPPUserService {

    @Autowired
    private TurXMPPUserRepository userRepository;

    @Override
    public void createUser(TurXMPPUser user) {
        user.setPassword(encodePassword(user.getPassword()));
        userRepository.save(user);
    }

    @Override
    @CacheEvict(value = "users", key = "#user.username")
    public void updateUser(TurXMPPUser user) {
    	TurXMPPUser dbUser = findById(user.getId());
        dbUser.setUsername(user.getUsername());
        dbUser.setAdmin(user.isAdmin());
        String newPassword = user.getPassword();
        if (newPassword != null && newPassword.length() > 0) {
            dbUser.setPassword(encodePassword(newPassword));
        }
        userRepository.save(dbUser);
    }

    @Override
    @CacheEvict(value = "users", key = "#username")
    public void changePassword(String username, String newPassword) {
    	TurXMPPUser user = findByUsername(username);
        if (user != null) {
            user.setPassword(encodePassword(newPassword));
            userRepository.save(user);
        }
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public void removeUser(Long userId) {
        userRepository.delete(userId);
    }

    @Override
    public TurXMPPUser findById(Long userId) {
    	TurXMPPUser user = userRepository.findById(userId).get();
        if (user == null) {
            throw new IllegalArgumentException("User not found by userId = " + userId);
        }
        return user;
    }

    @Override
    @Cacheable(value = "users", key = "#username", unless = "#result == null")
    public TurXMPPUser findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public Page<TurXMPPUser> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
    
    @Override
    public boolean verifyCredentials(String username, String passwordCleartext) {
    	TurXMPPUser user = findByUsername(username);
        return user != null && comparePasswords(passwordCleartext, user.getPassword());
    }

    private String encodePassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private boolean comparePasswords(String passwordCleartext, String encodedPassword) {
        return passwordCleartext != null && encodedPassword != null
                && BCrypt.checkpw(passwordCleartext, encodedPassword);
    }
}
