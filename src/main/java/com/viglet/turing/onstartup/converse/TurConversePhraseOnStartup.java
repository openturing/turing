/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.onstartup.converse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.converse.TurConversePhrase;
import com.viglet.turing.persistence.repository.converse.TurConversePhraseRepository;

@Component
@Transactional
public class TurConversePhraseOnStartup {

	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;

	public void createDefaultRows() {

		if (turConversePhraseRepository.findAll().isEmpty()) {

			TurConversePhrase turConversePhrase = new TurConversePhrase();
			turConversePhrase.setText("Olá");
			turConversePhraseRepository.save(turConversePhrase);
			
			turConversePhrase = new TurConversePhrase();
			turConversePhrase.setText("Como vai?");
			turConversePhraseRepository.save(turConversePhrase);
		
		}
	}

}