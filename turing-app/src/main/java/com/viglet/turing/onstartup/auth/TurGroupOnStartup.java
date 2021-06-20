/*
 * Copyright (C) 2016-2018 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;

@Component
public class TurGroupOnStartup {
	@Autowired
	private TurGroupRepository turGroupRepository;

	public void createDefaultRows() {

		if (turGroupRepository.findAll().isEmpty()) {

			TurGroup turGroup = new TurGroup();

			turGroup.setName("Administrator");
			turGroup.setDescription("Administrator Group");
			turGroupRepository.save(turGroup);
		}

	}
}
