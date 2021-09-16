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

import java.util.List;

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

import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/api/v2/role")
@Api(tags = "Role", description = "Role API")
public class TurRoleAPI {

	@Autowired
	private TurRoleRepository turRoleRepository;

	@GetMapping
	public List<TurRole> turRoleList() {
		return turRoleRepository.findAll();
	}

	@GetMapping("/{id}")
	public TurRole turRoleEdit(@PathVariable String id) {
		return turRoleRepository.findById(id).orElse(new TurRole());
	}

	@PutMapping("/{id}")
	public TurRole turRoleUpdate(@PathVariable String id, @RequestBody TurRole turRole) throws Exception {
		turRoleRepository.save(turRole);
		return turRole;
	}

	@Transactional
	@DeleteMapping("/{id}")
	public boolean turRoleDelete(@PathVariable String id) {
		turRoleRepository.delete(id);
		return true;
	}

	@PostMapping
	public TurRole turRoleAdd(@RequestBody TurRole turRole) {

		turRoleRepository.save(turRole);

		return turRole;
	}

	@GetMapping("/model")
	public TurRole turRoleStructure() {
		return new TurRole();

	}

}
