package com.viglet.turing.api.system;

import java.util.List;

import org.json.JSONException;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/locale")
@Api(tags = "Locale", description = "Locale API")
public class TurLocaleAPI {

	@Autowired
	TurLocaleRepository turLocaleRepository;

	@ApiOperation(value = "Locale List")
	@GetMapping
	public List<TurLocale> turLocaleList() throws JSONException {
		return this.turLocaleRepository.findAll();
	}

	@ApiOperation(value = "Show a Locale")
	@GetMapping("/{id}")
	public TurLocale turLocaleGet(@PathVariable String id) throws JSONException {
		return this.turLocaleRepository.findById(id).get();
	}

	@ApiOperation(value = "Update a Locle")
	@PutMapping("/{id}")
	public TurLocale turLocaleUpdate(@PathVariable String id, @RequestBody TurLocale turLocale) throws Exception {
		TurLocale turLocaleEdit = this.turLocaleRepository.findById(id).get();
		turLocaleEdit.setEn(turLocale.getEn());
		turLocaleEdit.setPt(turLocale.getPt());
		this.turLocaleRepository.save(turLocaleEdit);
		return turLocaleEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Locale")
	@DeleteMapping("/{id}")
	public boolean turLocaleDelete(@PathVariable String id) throws Exception {
		this.turLocaleRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Locale")
	@PostMapping
	public TurLocale turLocaleAdd(@RequestBody TurLocale turLocale) throws Exception {
		this.turLocaleRepository.save(turLocale);
		return turLocale;

	}
}
