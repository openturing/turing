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

package com.viglet.turing.persistence.repository.auth;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;

public interface TurUserRepository extends JpaRepository<TurUser, String> {

	List<TurUser> findAll();

	TurUser findByUsername(String username);
	
	boolean existsByUsernameAndPassword(String username, String password);

	@SuppressWarnings("unchecked")
	TurUser save(TurUser turUser);

	Set<TurUser> findByTurGroupsIn(Collection<TurGroup> groups);

	@Modifying
	@Query("delete from TurUser p where p.username = ?1")
	void delete(String id);
}
