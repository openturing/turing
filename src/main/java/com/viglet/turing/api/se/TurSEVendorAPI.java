package com.viglet.turing.api.se;

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

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/se/vendor")
public class TurSEVendorAPI {
	
	@Autowired
	TurSEVendorRepository turSEVendorRepository;
	
	
	@ApiOperation(value = "Search Engine Vendor List")
	@GetMapping
	public List<TurSEVendor> list() throws JSONException {
		return this.turSEVendorRepository.findAll();
	}

	@ApiOperation(value = "Show a Search Engine Vendor")
	@GetMapping("/{id}")
	public TurSEVendor seSolution(@PathVariable String id) throws JSONException {
		return this.turSEVendorRepository.findById(id).get();
	}
	

	@ApiOperation(value = "Update a Search Engine Vendor")
	@PutMapping("/{id}")
	public TurSEVendor update(@PathVariable String id, @RequestBody TurSEVendor turSEVendor) throws Exception {
		TurSEVendor turSEVendorEdit = this.turSEVendorRepository.findById(id).get();
		turSEVendorEdit.setDescription(turSEVendor.getDescription());
		turSEVendorEdit.setPlugin(turSEVendor.getPlugin());
		turSEVendorEdit.setTitle(turSEVendor.getTitle());
		turSEVendorEdit.setWebsite(turSEVendor.getWebsite());		
		this.turSEVendorRepository.save(turSEVendorEdit);
		return turSEVendorEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Search Engine Vendor")
	@DeleteMapping("/{id}")
	public boolean deleteEntity(@PathVariable String id) {
		this.turSEVendorRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Search Engine Vendor")
	@PostMapping
	public TurSEVendor add(@RequestBody TurSEVendor turSEVendor) throws Exception {
		this.turSEVendorRepository.save(turSEVendor);
		return turSEVendor;

	}
}
