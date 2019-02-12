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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.solr.TurSolr;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/se")
@Api(tags = "Search Engine", description = "Search Engine API")
public class TurSEInstanceAPI {

	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;

	@ApiOperation(value = "Search Engine List")
	@GetMapping
	public List<TurSEInstance> turSEInstanceList() throws JSONException {
		return this.turSEInstanceRepository.findAll();
	}

	@ApiOperation(value = "Show a Search Engine")
	@GetMapping("/{id}")
	public TurSEInstance turSEInstanceGet(@PathVariable int id) throws JSONException {
		return this.turSEInstanceRepository.findById(id);
	}

	@ApiOperation(value = "Update a Search Engine")
	@PutMapping("/{id}")
	public TurSEInstance turSEInstanceUpdate(@PathVariable int id, @RequestBody TurSEInstance turSEInstance)
			throws Exception {
		TurSEInstance turSEInstanceEdit = turSEInstanceRepository.findById(id);
		turSEInstanceEdit.setTitle(turSEInstance.getTitle());
		turSEInstanceEdit.setDescription(turSEInstance.getDescription());
		turSEInstanceEdit.setTurSEVendor(turSEInstance.getTurSEVendor());
		turSEInstanceEdit.setHost(turSEInstance.getHost());
		turSEInstanceEdit.setPort(turSEInstance.getPort());
		turSEInstanceEdit.setEnabled(turSEInstance.getEnabled());
		this.turSEInstanceRepository.save(turSEInstanceEdit);
		return turSEInstanceEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Search Engine")
	@DeleteMapping("/{id}")
	public boolean turSEInstanceDelete(@PathVariable int id) throws Exception {
		this.turSEInstanceRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Search Engine")
	@PostMapping
	public TurSEInstance turSEInstanceAdd(@RequestBody TurSEInstance turSEInstance) throws Exception {
		this.turSEInstanceRepository.save(turSEInstance);
		return turSEInstance;

	}

	@GetMapping("/select")
	public String turSEInstanceSelect(@RequestParam(required = false, name = "q") String q,
			@RequestParam(required = false, name = "p") Integer currentPage,
			@RequestParam(required = false, name = "fq[]") List<String> fq,
			@RequestParam(required = false, name = "sort") String sort,
			@RequestParam(required = false, name = "rows") Integer rows) throws JSONException {

		if (currentPage == null || currentPage <= 0)
			currentPage = 1;

		if (rows == null)
			rows = 0;

		String result = null;
		TurSolr turSolr = new TurSolr();
		try {
			result = turSolr.retrieveSolr(q, fq, currentPage, sort, rows).toString();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

}