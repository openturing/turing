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

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/se")
public class TurSEInstanceAPI {

	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;

	@ApiOperation(value = "Search Engine List")
	@GetMapping
	public List<TurSEInstance> list() throws JSONException {
		return this.turSEInstanceRepository.findAll();
	}

	@ApiOperation(value = "Show a Search Engine")
	@GetMapping("/{id}")
	public TurSEInstance dataGroup(@PathVariable int id) throws JSONException {
		return this.turSEInstanceRepository.findById(id);
	}

	@ApiOperation(value = "Update a Search Engine")
	@PutMapping("/{id}")
	public TurSEInstance update(@PathVariable int id, @RequestBody TurSEInstance turSEInstance) throws Exception {
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
	public boolean delete(@PathVariable int id) throws Exception {
		this.turSEInstanceRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Search Engine")
	@PostMapping
	public TurSEInstance add(@RequestBody TurSEInstance turSEInstance) throws Exception {
		this.turSEInstanceRepository.save(turSEInstance);
		return turSEInstance;

	}

	@GetMapping("/select")
	public String select(@RequestParam("q") String q, @RequestParam("p") int p, @RequestParam("fq[]") List<String> fq,
			@RequestParam("sort") String sort) throws JSONException {
		String result = null;
		TurSolr turSolr = new TurSolr();
		try {
			result = turSolr.retrieveSolr(q, fq, p, sort).toString();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

}