package com.viglet.turing.api.sn;

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

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;


@RestController
@RequestMapping("/api/sn")
@Api(tags = "Semantic Navigation Site", description = "Semantic Navigation Site API")
public class TurSNSiteAPI {
	
	@Autowired
	TurSNSiteRepository turSNSiteRepository;

	@ApiOperation(value = "Semantic Navigation Site List")
	@GetMapping
	public List<TurSNSite> list() throws JSONException {
		 return this.turSNSiteRepository.findAll();
	}

	@ApiOperation(value = "Show a Semantic Navigation Site")
	@GetMapping("/{id}")
	public TurSNSite dataGroup(@PathVariable int id) throws JSONException {
		 return this.turSNSiteRepository.findById(id);
	}
	
	@ApiOperation(value = "Update a Semantic Navigation Site")
	@PutMapping("/{id}")
	public TurSNSite turSNSiteUpdate(@PathVariable int id, @RequestBody TurSNSite turSNSite) throws Exception {
		TurSNSite turSNSiteEdit = this.turSNSiteRepository.findById(id);
		turSNSiteEdit.setName(turSNSite.getName());
		turSNSiteEdit.setDescription(turSNSite.getDescription());
		turSNSiteEdit.setLanguage(turSNSite.getLanguage());
		turSNSiteEdit.setTurSEInstance(turSNSite.getTurSEInstance());
		turSNSiteEdit.setTurNLPInstance(turSNSite.getTurNLPInstance());
		turSNSiteEdit.setCore(turSNSite.getCore());
		//UI
		turSNSiteEdit.setFacet(turSNSite.getFacet());
		turSNSiteEdit.setHl(turSNSite.getHl());
		turSNSiteEdit.setHlPost(turSNSite.getHlPost());
		turSNSiteEdit.setHlPre(turSNSite.getHlPre());
		turSNSiteEdit.setItemsPerFacet(turSNSite.getItemsPerFacet());
		turSNSiteEdit.setMlt(turSNSite.getMlt());
		turSNSiteEdit.setRowsPerPage(turSNSite.getRowsPerPage());
		this.turSNSiteRepository.save(turSNSiteEdit);
		return turSNSiteEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Semantic Navigation Site")
	@DeleteMapping("/{id}")
	public boolean turSNSiteDelete(@PathVariable int id) throws Exception {
		this.turSNSiteRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Semantic Navigation Site")
	@PostMapping
	public TurSNSite turSNSiteAdd(TurSNSite turSNSite) throws Exception {
		this.turSNSiteRepository.save(turSNSite);
		return turSNSite;

	}

}