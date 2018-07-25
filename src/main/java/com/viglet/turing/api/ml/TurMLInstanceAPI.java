package com.viglet.turing.api.ml;

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

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.repository.ml.TurMLInstanceRepository;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml")
public class TurMLInstanceAPI {

	@Autowired
	TurMLInstanceRepository turMLInstanceRepository;

	@ApiOperation(value = "Machine Learning List")
	@GetMapping
	public List<TurMLInstance> list() throws JSONException {
		return this.turMLInstanceRepository.findAll();
	}

	@ApiOperation(value = "Show a Machine Learning")
	@GetMapping("/{id}")
	public TurMLInstance detailService(@PathVariable int id) throws JSONException {
		return this.turMLInstanceRepository.findById(id);
	}

	@ApiOperation(value = "Update a Machine Learning")
	@PutMapping("/{id}")
	public TurMLInstance update(@PathVariable int id, @RequestBody TurMLInstance turMLInstance) throws Exception {
		TurMLInstance turMLInstanceEdit = this.turMLInstanceRepository.findById(id);
		turMLInstanceEdit.setTitle(turMLInstance.getTitle());
		turMLInstanceEdit.setDescription(turMLInstance.getDescription());
		turMLInstanceEdit.setTurMLVendor(turMLInstance.getTurMLVendor());
		turMLInstanceEdit.setHost(turMLInstance.getHost());
		turMLInstanceEdit.setPort(turMLInstance.getPort());
		turMLInstanceEdit.setEnabled(turMLInstance.getEnabled());
		this.turMLInstanceRepository.save(turMLInstanceEdit);
		return turMLInstanceEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning")
	@DeleteMapping("/{id}")
	public boolean deleteEntity(@PathVariable int id) {
		this.turMLInstanceRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning")
	@PostMapping
	public TurMLInstance add(@RequestBody TurMLInstance turMLInstance) throws Exception {
		this.turMLInstanceRepository.save(turMLInstance);
		return turMLInstance;

	}
}