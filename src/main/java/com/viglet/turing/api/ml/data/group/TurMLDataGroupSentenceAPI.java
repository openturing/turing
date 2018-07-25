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

import com.viglet.turing.bean.ml.sentence.TurMLSentenceBean;
import com.viglet.turing.persistence.bean.storage.TurDataGroupSentenceBean;
import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.ml.TurMLCategoryRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/sentence")
@Api(tags = "Machine Learning Sentence by Group", description = "Machine Learning Sentence by Group API")
public class TurMLDataGroupSentenceAPI {

	@Autowired
	TurDataGroupRepository turDataGroupRepository;
	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	TurMLCategoryRepository turMLCategoryRepository;

	@ApiOperation(value = "Machine Learning Data Group Sentence List")
	@GetMapping
	public List<TurDataGroupSentence> turDataGroupSentenceList(@PathVariable int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = turDataGroupRepository.findById(dataGroupId);
		return this.turDataGroupSentenceRepository.findByTurDataGroup(turDataGroup);
	}

	@ApiOperation(value = "Show a Machine Learning Data Group Sentence")
	@GetMapping("/{id}")
	public TurDataGroupSentence turDataGroupSentenceGet(@PathVariable int dataGroupId,
			@PathVariable int id) throws JSONException {
		return this.turDataGroupSentenceRepository.findById(id);
	}

	@ApiOperation(value = "Update a Machine Learning Data Group Sentence")
	@PutMapping("/{id}")
	public TurDataGroupSentence turDataGroupSentenceUpdate(@PathVariable int dataGroupId,
			@PathVariable int id, @RequestBody TurDataGroupSentenceBean turDataGroupSentenceBean)
			throws Exception {
		TurDataGroupSentence turDataGroupSentenceEdit = this.turDataGroupSentenceRepository.findById(id);
		turDataGroupSentenceEdit.setSentence(turDataGroupSentenceBean.getSentence());
		turDataGroupSentenceEdit
				.setTurMLCategory(turMLCategoryRepository.findById(turDataGroupSentenceBean.getTurMLCategory()));
		this.turDataGroupSentenceRepository.save(turDataGroupSentenceEdit);
		return turDataGroupSentenceEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Data Group Sentence")
	@DeleteMapping("/{id}")
	public boolean turDataGroupSentenceDelete(@PathVariable int dataGroupId, @PathVariable int id) throws Exception  {
		this.turDataGroupSentenceRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Data Group Sentence")
	@PostMapping
	public TurDataGroupSentence turDataGroupSentenceAdd(@PathVariable int dataGroupId, @RequestBody TurMLSentenceBean turMLSentenceBean)
			throws Exception {
		TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
		TurDataGroup turDataGroup = this.turDataGroupRepository.findById(dataGroupId);

		turDataGroupSentence.setSentence(turMLSentenceBean.getSentence());
		turDataGroupSentence.setTurDataGroup(turDataGroup);

		TurMLCategory turMLCategory = this.turMLCategoryRepository.findById(turMLSentenceBean.getTurMLCategoryId());
		if (turMLCategory != null) {
			turDataGroupSentence.setTurMLCategory(turMLCategory);
		}
		
		this.turDataGroupSentenceRepository.save(turDataGroupSentence);
		return turDataGroupSentence;

	}
}
