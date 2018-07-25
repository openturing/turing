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

import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.repository.nlp.term.TurTermRepository;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/entity/terms")
public class TurNLPEntityTermAPI {
	@Autowired
	private TurTermRepository turTermRepository;
	
	@ApiOperation(value = "Entity Term list")
	@GetMapping
	public List<TurTerm> list() throws JSONException {
		return this.turTermRepository.findAll();
	}

	@ApiOperation(value = "Show a Entity Term")
	@GetMapping("/{id}")
	public TurTerm detail(@PathVariable int id) throws JSONException {
		return this.turTermRepository.getOne(id);
	}

	@ApiOperation(value = "Update a Entity Term")
	@PutMapping("/{id}")
	public TurTerm update(@PathVariable int id, @RequestBody TurTerm turTerm) throws Exception {
		TurTerm turTermEdit = this.turTermRepository.getOne(id);
		turTermEdit.setName(turTerm.getName());
		turTermEdit.setIdCustom(turTerm.getIdCustom());
		this.turTermRepository.save(turTermEdit);
		return turTermEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Entity Term")
	@DeleteMapping("/{id}")
	public boolean deleteEntity(@PathVariable int id) {
		this.turTermRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Entity Term")
	@PostMapping
	public TurTerm add(@RequestBody TurTerm turTerm) throws Exception {
		this.turTermRepository.save(turTerm);
		return turTerm;

	}
}
