package com.viglet.turing.api.entity;

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

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/entity")
public class TurNLPEntityAPI {
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;

	@ApiOperation(value = "Entity list")
	@GetMapping
	public List<TurNLPEntity> list() throws JSONException {
		return this.turNLPEntityRepository.findAll();
	}

	@ApiOperation(value = "Local Entity list")
	@GetMapping("/local")
	public List<TurNLPEntity> listLocal() throws JSONException {
		return this.turNLPEntityRepository.findByLocal(1);
	}
	
	@ApiOperation(value = "Show a entity")
	@GetMapping("/{id}")
	public TurNLPEntity detail(@PathVariable int id) throws JSONException {
		return this.turNLPEntityRepository.findById(id);
	}

	@ApiOperation(value = "Update a entity")
	@PutMapping("/{id}")
	public TurNLPEntity update(@PathVariable int id, @RequestBody TurNLPEntity turNLPEntity) throws Exception {
		TurNLPEntity turNLPEntityEdit =  this.turNLPEntityRepository.findById(id);
		turNLPEntityEdit.setName(turNLPEntity.getName());
		turNLPEntityEdit.setDescription(turNLPEntity.getDescription());
		this.turNLPEntityRepository.save(turNLPEntityEdit);
		return turNLPEntityEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a entity")
	@DeleteMapping("/{id}")
	public boolean deleteEntity(@PathVariable int id) {
		TurNLPEntity turNLPEntity =  this.turNLPEntityRepository.findById(id);
		this.turNLPEntityRepository.delete(turNLPEntity);
		return true;
	}

	@ApiOperation(value = "Create a entity")
	@PostMapping
	public TurNLPEntity add(@RequestBody TurNLPEntity turNLPEntity) throws Exception {
		this.turNLPEntityRepository.save(turNLPEntity);
		return turNLPEntity;

	}
}