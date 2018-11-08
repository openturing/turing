package com.viglet.turing.api.sn;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.turing.exchange.sn.TurSNSiteExport;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/sn")
@Api(tags = "Semantic Navigation Site", description = "Semantic Navigation Site API")
@ComponentScan("com.viglet.turing") 
public class TurSNSiteAPI {

	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	TurSNSiteExport turSNSiteExport;
	
	@ApiOperation(value = "Semantic Navigation Site List")
	@GetMapping
	public List<TurSNSite> list() throws JSONException {
		return this.turSNSiteRepository.findAll();
	}

	@ApiOperation(value = "Show a Semantic Navigation Site")
	@GetMapping("/{id}")
	public TurSNSite dataGroup(@PathVariable String id) throws JSONException {
		return this.turSNSiteRepository.findById(id).get();
	}

	@ApiOperation(value = "Update a Semantic Navigation Site")
	@PutMapping("/{id}")
	public TurSNSite turSNSiteUpdate(@PathVariable String id, @RequestBody TurSNSite turSNSite) throws Exception {
		TurSNSite turSNSiteEdit = this.turSNSiteRepository.findById(id).get();
		turSNSiteEdit.setName(turSNSite.getName());
		turSNSiteEdit.setDescription(turSNSite.getDescription());
		turSNSiteEdit.setLanguage(turSNSite.getLanguage());
		turSNSiteEdit.setTurSEInstance(turSNSite.getTurSEInstance());		
		turSNSiteEdit.setTurNLPInstance(turSNSite.getTurNLPInstance());
		turSNSiteEdit.setThesaurus(turSNSite.getThesaurus());
		turSNSiteEdit.setCore(turSNSite.getCore());
		// UI
		turSNSiteEdit.setFacet(turSNSite.getFacet());
		turSNSiteEdit.setHl(turSNSite.getHl());
		turSNSiteEdit.setHlPost(turSNSite.getHlPost());
		turSNSiteEdit.setHlPre(turSNSite.getHlPre());
		turSNSiteEdit.setItemsPerFacet(turSNSite.getItemsPerFacet());
		turSNSiteEdit.setMlt(turSNSite.getMlt());
		turSNSiteEdit.setRowsPerPage(turSNSite.getRowsPerPage());
		turSNSiteEdit.setDefaultTitleField(turSNSite.getDefaultTitleField());
		turSNSiteEdit.setDefaultTextField(turSNSite.getDefaultTextField());
		turSNSiteEdit.setDefaultDescriptionField(turSNSite.getDefaultDescriptionField());
		turSNSiteEdit.setDefaultDateField(turSNSite.getDefaultDateField());
		turSNSiteEdit.setDefaultImageField(turSNSite.getDefaultImageField());
		turSNSiteEdit.setDefaultURLField(turSNSite.getDefaultURLField());
		
		this.turSNSiteRepository.save(turSNSiteEdit);
		return turSNSiteEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Semantic Navigation Site")
	@DeleteMapping("/{id}")
	public boolean turSNSiteDelete(@PathVariable String id) throws Exception {
		this.turSNSiteRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Semantic Navigation Site")
	@PostMapping
	public TurSNSite turSNSiteAdd(@RequestBody TurSNSite turSNSite) throws Exception {
		this.turSNSiteRepository.save(turSNSite);
		return turSNSite;

	}

	@ResponseBody
	@GetMapping(value = "/export", produces = "application/zip")
	public StreamingResponseBody turSNSiteExport(HttpServletResponse response) throws Exception {
		
		return turSNSiteExport.exportObject(response);

	}

}