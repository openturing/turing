package com.viglet.turing.api.nlp;

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

import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/nlp/vendor")
@Api(tags = "Natural Language Processing Vendor", description = "Natural Language Processing Vendor API")
public class TurNLPVendorAPI {
	
	@Autowired
	TurNLPVendorRepository turNLPVendorRepository;
	
	@ApiOperation(value = "Natural Language Processing Vendor List")
	@GetMapping
	public List<TurNLPVendor> turNLPVendorList() throws JSONException {
		return this.turNLPVendorRepository.findAll();
	}

	@ApiOperation(value = "Show a Natural Language Processing Vendor")
	@GetMapping("/{id}")
	public TurNLPVendor turNLPVendorGet(@PathVariable String id) throws JSONException {
		return this.turNLPVendorRepository.findById(id).get();
	}
	

	@ApiOperation(value = "Update a Natural Language Processing")
	@PutMapping("/{id}")
	public TurNLPVendor turNLPVendorUpdate(@PathVariable String id, @RequestBody TurNLPVendor turNLPVendor) throws Exception {
		TurNLPVendor turNLPVendorEdit = this.turNLPVendorRepository.findById(id).get();
		turNLPVendorEdit.setDescription(turNLPVendor.getDescription());
		turNLPVendorEdit.setPlugin(turNLPVendor.getPlugin());
		turNLPVendorEdit.setTitle(turNLPVendor.getTitle());
		turNLPVendorEdit.setWebsite(turNLPVendor.getWebsite());		
		this.turNLPVendorRepository.save(turNLPVendorEdit);
		return turNLPVendorEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Natural Language Processing Vendor")
	@DeleteMapping("/{id}")
	public boolean turNLPVendorDelete(@PathVariable String id) {
		this.turNLPVendorRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Natural Language Processing Vendor")
	@PostMapping
	public TurNLPVendor turNLPVendorAdd(@RequestBody TurNLPVendor turNLPVendor) throws Exception {
		this.turNLPVendorRepository.save(turNLPVendor);
		return turNLPVendor;

	}
}
