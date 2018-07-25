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

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.repository.ml.TurMLVendorRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/vendor")
@Api(tags = "Machine Learning Vendor", description = "Machine Learning Vendor API")
public class TurMLVendorAPI {

	@Autowired
	TurMLVendorRepository turMLVendorRepository;

	@ApiOperation(value = "Machine Learning Vendor List")
	@GetMapping
	public List<TurMLVendor> turMLVendorList() throws JSONException {
		return this.turMLVendorRepository.findAll();
	}

	@ApiOperation(value = "Show a Machine Learning Vendor")
	@GetMapping("/{id}")
	public TurMLVendor turMLVendorGet(@PathVariable String id) throws JSONException {
		return this.turMLVendorRepository.findById(id).get();
	}

	@ApiOperation(value = "Update a Machine Learning Vendor")
	@PutMapping("/{id}")
	public TurMLVendor turMLVendorUpdate(@PathVariable String id, @RequestBody TurMLVendor turMLVendor) throws Exception {
		TurMLVendor turMLVendorEdit = this.turMLVendorRepository.findById(id).get();
		turMLVendorEdit.setDescription(turMLVendor.getDescription());
		turMLVendorEdit.setPlugin(turMLVendor.getPlugin());
		turMLVendorEdit.setTitle(turMLVendor.getTitle());
		turMLVendorEdit.setWebsite(turMLVendor.getWebsite());
		this.turMLVendorRepository.save(turMLVendorEdit);
		return turMLVendorEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Vendor")
	@DeleteMapping("/{id}")
	public boolean turMLVendorDelete(@PathVariable String id) {
		this.turMLVendorRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Vendor")
	@PostMapping
	public TurMLVendor turMLVendorAdd(@RequestBody TurMLVendor turMLVendor) throws Exception {
		this.turMLVendorRepository.save(turMLVendor);
		return turMLVendor;

	}
}
