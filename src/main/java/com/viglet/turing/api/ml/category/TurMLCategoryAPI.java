package com.viglet.turing.api.ml.category;

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

import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.repository.ml.TurMLCategoryRepository;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/category")
public class TurMLCategoryAPI {

	@Autowired
	TurMLCategoryRepository turMLCategoryRepository;

	@ApiOperation(value = "Machine Learning Category List")
	@GetMapping
	public List<TurMLCategory> list() throws JSONException {
		return this.turMLCategoryRepository.findAll();
	}

	@ApiOperation(value = "Show a Machine Learning Category")
	@GetMapping("/{id}")
	public TurMLCategory mlSolution(@PathVariable int id) throws JSONException {
		return this.turMLCategoryRepository.findById(id);
	}

	@ApiOperation(value = "Update a Machine Learning Category")
	@PutMapping("/{id}")
	public TurMLCategory update(@PathVariable int id, @RequestBody TurMLCategory turMLCategory) throws Exception {
		TurMLCategory turMLCategoryEdit = this.turMLCategoryRepository.findById(id);
		turMLCategoryEdit.setInternalName(turMLCategory.getInternalName());
		turMLCategoryEdit.setName(turMLCategory.getName());
		turMLCategoryEdit.setDescription(turMLCategory.getDescription());
		this.turMLCategoryRepository.save(turMLCategoryEdit);
		this.turMLCategoryRepository.flush();

		return turMLCategoryEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Category")
	@DeleteMapping("/{id}")
	public boolean deleteEntity(@PathVariable int id) {
		this.turMLCategoryRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Category")
	@PostMapping
	public TurMLCategory add(@RequestBody TurMLCategory turMLCategory) throws Exception {
		this.turMLCategoryRepository.save(turMLCategory);
		return turMLCategory;

	}
}
