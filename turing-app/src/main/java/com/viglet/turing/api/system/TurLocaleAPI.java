/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.api.system;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/locale")
@Tag(name = "Locale", description = "Locale API")
public class TurLocaleAPI {

	@Autowired
	private TurLocaleRepository turLocaleRepository;

	@Operation(summary = "Locale List")
	@GetMapping
	public List<TurLocale> turLocaleList() {
		return this.turLocaleRepository.findAll();
	}

	@Operation(summary = "Show a Locale")
	@GetMapping("/{id}")
	public TurLocale turLocaleGet(@PathVariable String id) {
		return this.turLocaleRepository.findById(id).orElse(new TurLocale());
	}

	@Operation(summary = "Update a Locle")
	@PutMapping("/{id}")
	public TurLocale turLocaleUpdate(@PathVariable String id, @RequestBody TurLocale turLocale) {
		return this.turLocaleRepository.findById(id).map(turLocaleEdit -> {
			turLocaleEdit.setEn(turLocale.getEn());
			turLocaleEdit.setPt(turLocale.getPt());
			this.turLocaleRepository.save(turLocaleEdit);
			return turLocaleEdit;
		}).orElse(new TurLocale());

	}

	@Transactional
	@Operation(summary = "Delete a Locale")
	@DeleteMapping("/{id}")
	public boolean turLocaleDelete(@PathVariable String id) {
		this.turLocaleRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Locale")
	@PostMapping
	public TurLocale turLocaleAdd(@RequestBody TurLocale turLocale) {
		this.turLocaleRepository.save(turLocale);
		return turLocale;

	}
}
