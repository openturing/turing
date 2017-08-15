package com.viglet.turing.se.field;

import java.util.HashMap;
import java.util.Map;

public class TurSEFieldMaps {
	Map<String, TurSEFieldMap> fieldMaps = new HashMap<String, TurSEFieldMap>();
	
	public Map<String, TurSEFieldMap> getFieldMaps() {
		return fieldMaps;
	}

	public void setFieldMaps(Map<String, TurSEFieldMap> fieldMaps) {
		this.fieldMaps = fieldMaps;
	}

	public TurSEFieldMaps() {		
		fieldMaps.put("id", new TurSEFieldMap("id", TurSEFieldType.STRING, "dc:identifier", null));
		fieldMaps.put("type", new TurSEFieldMap("type", TurSEFieldType.ARRAY, "dc:type", null));
		fieldMaps.put("elevate", new TurSEFieldMap("elevate", TurSEFieldType.STRING, "otsn:elevate", null));
		fieldMaps.put("title", new TurSEFieldMap("title", TurSEFieldType.STRING, "dc:title", null));
		fieldMaps.put("author", new TurSEFieldMap("author", TurSEFieldType.STRING, "dc:creator", null));	
		//fieldMaps.put("date", new TurSEFieldMap("date", TurSEFieldType.STRING, "dc:date", null)); //'%Y-%m-%d'
		fieldMaps.put("image", new TurSEFieldMap("image", TurSEFieldType.STRING, "otsn:image", null));
		fieldMaps.put("text", new TurSEFieldMap("text", TurSEFieldType.STRING, "text", null));
		fieldMaps.put("abstract", new TurSEFieldMap("abstract", TurSEFieldType.STRING, "dc:description", null));
		fieldMaps.put("section", new TurSEFieldMap("section", TurSEFieldType.STRING, "dc:section", null));
		fieldMaps.put("subsection", new TurSEFieldMap("subsection", TurSEFieldType.STRING, "dc:subsection", null));
		fieldMaps.put("url", new TurSEFieldMap("url", TurSEFieldType.STRING, "rdf:about", null));
		fieldMaps.put("last_published", new TurSEFieldMap("last_published", TurSEFieldType.STRING, "dc:date", null));
		fieldMaps.put("original_date", new TurSEFieldMap("original_date", TurSEFieldType.DATE, "original_date", null));
		fieldMaps.put("sebna_tipo", new TurSEFieldMap("sebna_tipo", TurSEFieldType.STRING, "sebna:tipo", null));
		
		//Corrigir no broker
		fieldMaps.put("sebna_destaque", new TurSEFieldMap("sebna_destaque", TurSEFieldType.BOOL, "sebna:destaque", new TurSEFieldRequired(true, false)));
		fieldMaps.put("sebna_nacional", new TurSEFieldMap("sebna_nacional", TurSEFieldType.BOOL, "sebna:nacional", null));
		fieldMaps.put("sebna_mobile", new TurSEFieldMap("sebna_mobile", TurSEFieldType.BOOL, "sebna:mobile", null));
		fieldMaps.put("sebna_tema", new TurSEFieldMap("sebna_tema", TurSEFieldType.STRING, "sebna:tema", null));
		fieldMaps.put("sebna_dtini_destaque", new TurSEFieldMap("sebna_dtini_destaque", TurSEFieldType.DATE, "sebna:dtini_destaque", null));
		fieldMaps.put("sebna_dtfin_destaque", new TurSEFieldMap("sebna_dtfin_destaque", TurSEFieldType.DATE, "sebna:dtfin_destaque", null));
		
		//Corrigir no broker
		fieldMaps.put("sebna_qtd_visualizacao", new TurSEFieldMap("sebna_qtd_visualizacao", TurSEFieldType.INT, "sebna:qtdVisualizacao", null));
		fieldMaps.put("sebna_perfil", new TurSEFieldMap("sebna_perfil", TurSEFieldType.STRING, "sebna:perfil", null));

		//Corrigir no broker
		fieldMaps.put("sebna_uf", new TurSEFieldMap("sebna_uf", TurSEFieldType.STRING, "sebna:uf", null));	
		fieldMaps.put("sebna_segmento", new TurSEFieldMap("sebna_segmento", TurSEFieldType.STRING, "sebna:segmento", null));
		
		//Conferir se está montando Array
		fieldMaps.put("sebna_momento_startup", new TurSEFieldMap("sebna_momento_startup", TurSEFieldType.ARRAY, "sebna:momento_startup", null));
		
		//criar este atributo no broker
		fieldMaps.put("sebna:classtema", new TurSEFieldMap("sebna:classtema", TurSEFieldType.STRING, "sebna:classtema", null));
		
		
	}
	
	
}