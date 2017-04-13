package com.viglet.turing.se.field;

import java.util.HashMap;
import java.util.Map;

public class VigSEFieldMaps {
	Map<String, VigSEFieldMap> fieldMaps = new HashMap<String, VigSEFieldMap>();
	
	public Map<String, VigSEFieldMap> getFieldMaps() {
		return fieldMaps;
	}

	public void setFieldMaps(Map<String, VigSEFieldMap> fieldMaps) {
		this.fieldMaps = fieldMaps;
	}

	public VigSEFieldMaps() {		
		fieldMaps.put("id", new VigSEFieldMap("id", VigSEFieldType.STRING, "dc:identifier", null));
		fieldMaps.put("type", new VigSEFieldMap("type", VigSEFieldType.ARRAY, "dc:type", null));
		fieldMaps.put("elevate", new VigSEFieldMap("elevate", VigSEFieldType.STRING, "otsn:elevate", null));
		fieldMaps.put("title", new VigSEFieldMap("title", VigSEFieldType.STRING, "dc:title", null));
		fieldMaps.put("author", new VigSEFieldMap("author", VigSEFieldType.STRING, "dc:creator", null));	
		//fieldMaps.put("date", new VigSEFieldMap("date", VigSEFieldType.STRING, "dc:date", null)); //'%Y-%m-%d'
		fieldMaps.put("image", new VigSEFieldMap("image", VigSEFieldType.STRING, "otsn:image", null));
		fieldMaps.put("text", new VigSEFieldMap("text", VigSEFieldType.STRING, "text", null));
		fieldMaps.put("abstract", new VigSEFieldMap("abstract", VigSEFieldType.STRING, "dc:description", null));
		fieldMaps.put("section", new VigSEFieldMap("section", VigSEFieldType.STRING, "dc:section", null));
		fieldMaps.put("subsection", new VigSEFieldMap("subsection", VigSEFieldType.STRING, "dc:subsection", null));
		fieldMaps.put("url", new VigSEFieldMap("url", VigSEFieldType.STRING, "rdf:about", null));
		fieldMaps.put("last_published", new VigSEFieldMap("last_published", VigSEFieldType.STRING, "dc:date", null));
		fieldMaps.put("original_date", new VigSEFieldMap("original_date", VigSEFieldType.DATE, "original_date", null));
		fieldMaps.put("sebna_tipo", new VigSEFieldMap("sebna_tipo", VigSEFieldType.STRING, "sebna:tipo", null));
		
		//Corrigir no broker
		fieldMaps.put("sebna_destaque", new VigSEFieldMap("sebna_destaque", VigSEFieldType.BOOL, "sebna:destaque", new VigSEFieldRequired(true, false)));
		fieldMaps.put("sebna_nacional", new VigSEFieldMap("sebna_nacional", VigSEFieldType.BOOL, "sebna:nacional", null));
		fieldMaps.put("sebna_mobile", new VigSEFieldMap("sebna_mobile", VigSEFieldType.BOOL, "sebna:mobile", null));
		fieldMaps.put("sebna_tema", new VigSEFieldMap("sebna_tema", VigSEFieldType.STRING, "sebna:tema", null));
		fieldMaps.put("sebna_dtini_destaque", new VigSEFieldMap("sebna_dtini_destaque", VigSEFieldType.DATE, "sebna:dtini_destaque", null));
		fieldMaps.put("sebna_dtfin_destaque", new VigSEFieldMap("sebna_dtfin_destaque", VigSEFieldType.DATE, "sebna:dtfin_destaque", null));
		
		//Corrigir no broker
		fieldMaps.put("sebna_qtd_visualizacao", new VigSEFieldMap("sebna_qtd_visualizacao", VigSEFieldType.INT, "sebna:qtdVisualizacao", null));
		fieldMaps.put("sebna_perfil", new VigSEFieldMap("sebna_perfil", VigSEFieldType.STRING, "sebna:perfil", null));

		//Corrigir no broker
		fieldMaps.put("sebna_uf", new VigSEFieldMap("sebna_uf", VigSEFieldType.STRING, "sebna:uf", null));	
		fieldMaps.put("sebna_segmento", new VigSEFieldMap("sebna_segmento", VigSEFieldType.STRING, "sebna:segmento", null));
		
		//Conferir se está montando Array
		fieldMaps.put("sebna_momento_startup", new VigSEFieldMap("sebna_momento_startup", VigSEFieldType.ARRAY, "sebna:momento_startup", null));
		
		//criar este atributo no broker
		fieldMaps.put("sebna:classtema", new VigSEFieldMap("sebna:classtema", VigSEFieldType.STRING, "sebna:classtema", null));
		
		
	}
	
	
}