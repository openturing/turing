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

package com.viglet.turing.api.sn;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.bean.TurSNFieldExtType;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/sn/field/ext")
@Tag(name = "Semantic Navigation Field Ext", description = "Semantic Navigation Field Ext API")
public class TurSNFieldExtAPI {

	@Operation(summary = "Show a Semantic Navigation Site Field Ext Types")
	@GetMapping("/types")
	public List<TurSNFieldExtType> turSNFieldExtTypeGet() {
		List<TurSNFieldExtType> types = new ArrayList<>();
		types.add(new TurSNFieldExtType("BOOL", "Boolean"));
		types.add(new TurSNFieldExtType("DATE", "Date"));
		types.add(new TurSNFieldExtType("LONG", "Long"));
		types.add(new TurSNFieldExtType("INT", "Number"));
		types.add(new TurSNFieldExtType("STRING", "Text"));

		return types;
	}
}