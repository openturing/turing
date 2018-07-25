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

import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/nlp")
@Api(tags = "Natural Language Processing", description = "Natural Language Processing API")
public class TurNLPInstanceAPI {

	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurNLP turNLP;

	@ApiOperation(value = "Natural Language Processing List")
	@GetMapping
	public List<TurNLPInstance> turNLPInstanceList() throws JSONException {
		return this.turNLPInstanceRepository.findAll();
	}

	@ApiOperation(value = "Show a Natural Language Processing")
	@GetMapping("/{id}")
	public TurNLPInstance turNLPInstanceGet(@PathVariable int id) throws JSONException {
		return this.turNLPInstanceRepository.findById(id);
	}

	@ApiOperation(value = "Update a Natural Language Processing")
	@PutMapping("/{id}")
	public TurNLPInstance turNLPInstanceUpdate(@PathVariable int id, @RequestBody TurNLPInstance turNLPInstance) throws Exception {
		TurNLPInstance turNLPInstanceEdit = turNLPInstanceRepository.findById(id);
		turNLPInstanceEdit.setTitle(turNLPInstance.getTitle());
		turNLPInstanceEdit.setDescription(turNLPInstance.getDescription());
		turNLPInstanceEdit.setTurNLPVendor(turNLPInstance.getTurNLPVendor());
		turNLPInstanceEdit.setHost(turNLPInstance.getHost());
		turNLPInstanceEdit.setPort(turNLPInstance.getPort());
		turNLPInstanceEdit.setEnabled(turNLPInstance.getEnabled());
		turNLPInstanceEdit.setLanguage(turNLPInstance.getLanguage());
		this.turNLPInstanceRepository.save(turNLPInstanceEdit);
		return turNLPInstanceEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Natural Language Processing")
	@DeleteMapping("/{id}")
	public boolean turNLPInstanceDelete(@PathVariable int id) throws Exception {
		this.turNLPInstanceRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Natural Language Processing")
	@PostMapping
	public TurNLPInstance turNLPInstanceAdd(@RequestBody TurNLPInstance turNLPInstance) throws Exception {
		this.turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);
		return turNLPInstance;

	}

	@PostMapping("/{id}/validate")
	public String validate(@PathVariable int id, @RequestBody TurNLPTextValidate textValidate) throws JSONException {

		turNLP.startup(this.turNLPInstanceRepository.findById(id), textValidate.getText());
		return turNLP.validate().toString();
	}

	public boolean isNumeric(String str) {
		return str.matches("-?\\d+(\\.\\d+)?"); // match a number with optional
												// '-' and decimal.
	}

	public static class TurNLPTextValidate {
		String text;

		public TurNLPTextValidate() {
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}
}