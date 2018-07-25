package com.viglet.turing.api.ml.data.group;

import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
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
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupData;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.storage.TurDataGroupDataRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;
import com.viglet.turing.persistence.repository.storage.TurDataRepository;
import com.viglet.turing.plugins.opennlp.TurOpenNLPConnector;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/data")
public class TurMLDataGroupDataAPI {

	@Autowired
	TurDataGroupRepository turDataGroupRepository;
	@Autowired
	TurDataGroupDataRepository turDataGroupDataRepository;
	@Autowired
	TurDataRepository turDataRepository;
	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	TurOpenNLPConnector turOpenNLPConnector;

	@ApiOperation(value = "Machine Learning Data Group Data List")
	@GetMapping
	public List<TurDataGroupData> list(@PathVariable int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = this.turDataGroupRepository.getOne(dataGroupId);
		return this.turDataGroupDataRepository.findByTurDataGroup(turDataGroup);
	}

	@ApiOperation(value = "Show a Machine Learning Data Group Data")
	@GetMapping("/{id}")
	public TurDataGroupData mlSolution(@PathVariable int dataGroupId, @PathVariable int id) throws JSONException {
		return this.turDataGroupDataRepository.findById(id);
	}

	@ApiOperation(value = "Update a Machine Learning Data Group Data")
	@PutMapping("/{id}")
	public TurDataGroupData update(@PathVariable int dataGroupId, @PathVariable int id,
			@RequestBody TurData turMLData) throws Exception {
		TurDataGroupData turDataGroupDataEdit = this.turDataGroupDataRepository.getOne(id);
		turDataGroupDataEdit.setTurData(turMLData);
		this.turDataGroupDataRepository.save(turDataGroupDataEdit);
		return turDataGroupDataEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Data Group Data")
	@DeleteMapping("/{id}")
	public boolean deleteEntity(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupDataRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Data Group Data")
	@PostMapping
	public TurDataGroupData add(@PathVariable int dataGroupId, @RequestBody TurDataGroupData turDataGroupData)
			throws Exception {
		TurDataGroup turDataGroup = this.turDataGroupRepository.getOne(dataGroupId);
		turDataGroupData.setTurDataGroup(turDataGroup);
		this.turDataGroupDataRepository.save(turDataGroupData);
		return turDataGroupData;

	}

	@PostMapping("/import")	
	@Transactional
	public TurDataGroupData importData(@PathVariable int dataGroupId, @RequestParam("file") MultipartFile multipartFile)
			throws JSONException, IOException, SAXException, TikaException {
	
		TurDataGroup turDataGroup = this.turDataGroupRepository.getOne(dataGroupId);
		BodyContentHandler handler = new BodyContentHandler(-1);
		Metadata metadata = new Metadata();

		ParseContext pcontext = new ParseContext();

		// parsing the document using PDF parser
		PDFParser pdfparser = new PDFParser();
		pdfparser.parse(multipartFile.getInputStream(), handler, metadata, pcontext);

		String sentences[] = turOpenNLPConnector.sentenceDetect(handler.toString());

		TurData turData = new TurData();

		turData.setName(multipartFile.getOriginalFilename());
		turData.setType(FilenameUtils.getExtension(multipartFile.getOriginalFilename()));
		this.turDataRepository.save(turData);

		for (String sentence : sentences) {
			TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
			turDataGroupSentence.setTurData(turData);
			turDataGroupSentence.setSentence(sentence);
			turDataGroupSentence.setTurDataGroup(turDataGroup);
			turDataGroupSentenceRepository.save(turDataGroupSentence);
		}

		TurDataGroupData turDataGroupData = new TurDataGroupData();

		turDataGroupData.setTurData(turData);
		turDataGroupData.setTurDataGroup(turDataGroup);
		turDataGroupDataRepository.save(turDataGroupData);

		return turDataGroupData;
	}
}
