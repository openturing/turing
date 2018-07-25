package com.viglet.turing.api.ml.data.group;

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
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.repository.storage.TurDataGroupCategoryRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/category")
public class TurMLDataGroupCategoryAPI {

	@Autowired
	TurDataGroupRepository turDataGroupRepository;
	@Autowired
	TurDataGroupCategoryRepository turDataGroupCategoryRepository;

	@ApiOperation(value = "Machine Learning Data Group Category List")
	@GetMapping
	public List<TurDataGroupCategory> list(@PathVariable int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = turDataGroupRepository.getOne(dataGroupId);
		return this.turDataGroupCategoryRepository.findByTurDataGroup(turDataGroup);
	}

	@ApiOperation(value = "Show a Machine Learning Data Group Category")
	@GetMapping("/{id}")
	public TurDataGroupCategory mlSolution(@PathVariable int dataGroupId, @PathVariable int id) throws JSONException {
		return this.turDataGroupCategoryRepository.getOne(id);
	}

	@ApiOperation(value = "Update a Machine Learning Data Group Category")
	@PutMapping("/{id}")
	public TurDataGroupCategory update(@PathVariable int dataGroupId, @PathVariable int id,
			@RequestBody TurMLCategory turMLCategory) throws Exception {
		TurDataGroupCategory turDataGroupCategoryEdit = this.turDataGroupCategoryRepository.getOne(id);
		turDataGroupCategoryEdit.setTurMLCategory(turMLCategory);
		this.turDataGroupCategoryRepository.save(turDataGroupCategoryEdit);
		return turDataGroupCategoryEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Data Group Category")
	@DeleteMapping("/{id}")
	public boolean deleteEntity(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupCategoryRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Data Group Category")
	@PostMapping
	public TurDataGroupCategory add(@PathVariable int dataGroupId,
			@RequestBody TurDataGroupCategory turDataGroupCategory) throws Exception {
		TurDataGroup turDataGroup = turDataGroupRepository.getOne(dataGroupId);
		turDataGroupCategory.setTurDataGroup(turDataGroup);
		this.turDataGroupCategoryRepository.save(turDataGroupCategory);
		return turDataGroupCategory;

	}
}
