/*
 * Copyright (C) 2016-2021 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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

package com.viglet.turing.api.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/api/v2/group")
@Api(tags = "Group", description = "Group API")
public class TurGroupAPI {

	@Autowired
	private TurGroupRepository turGroupRepository;
	@Autowired
	private TurUserRepository turUserRepository;

	@GetMapping
	public List<TurGroup> turGroupList() {
		return turGroupRepository.findAll();
	}

	@GetMapping("/{id}")
	public TurGroup turGroupEdit(@PathVariable String id) {

		return turGroupRepository.findById(id).map(turGroup -> {
			List<TurGroup> turGroups = new ArrayList<>();
			turGroups = new ArrayList<>();
			turGroups.add(turGroup);
			turGroup.setTurUsers(turUserRepository.findByTurGroupsIn(turGroups));
			return turGroup;
		}).orElse(new TurGroup());
	}

	@PutMapping("/{id}")
	public TurGroup turGroupUpdate(@PathVariable String id, @RequestBody TurGroup turGroup) {
		turGroupRepository.save(turGroup);
		return turGroupRepository.findById(turGroup.getId()).map(turGroupRepos -> {
			List<TurGroup> turGroups = new ArrayList<>();
			turGroups.add(turGroup);
			Set<TurUser> turUsers = turUserRepository.findByTurGroupsIn(turGroups);
			for (TurUser turUser : turUsers) {
				turUser.getTurGroups().remove(turGroupRepos);
				turUserRepository.saveAndFlush(turUser);
			}
			for (TurUser turUser : turGroup.getTurUsers()) {
				TurUser turUserRepos = turUserRepository.findByUsername(turUser.getUsername());
				turUserRepos.getTurGroups().add(turGroup);
				turUserRepository.saveAndFlush(turUserRepos);
			}

			return turGroup;
		}).orElse(new TurGroup());
	}

	@Transactional
	@DeleteMapping("/{id}")
	public boolean turGroupDelete(@PathVariable String id) {
		turGroupRepository.delete(id);
		return true;
	}

	@PostMapping
	public TurGroup turGroupAdd(@RequestBody TurGroup turGroup) {

		turGroupRepository.save(turGroup);

		return turGroup;
	}

	@GetMapping("/model")
	public TurGroup turGroupStructure() {
		return new TurGroup();

	}

}
