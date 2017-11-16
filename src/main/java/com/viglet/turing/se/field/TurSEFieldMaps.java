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
		fieldMaps.put("id", new TurSEFieldMap("id", TurSEFieldType.STRING, "id", null));
		fieldMaps.put("type", new TurSEFieldMap("type", TurSEFieldType.STRING, "type", null));
		fieldMaps.put("elevate", new TurSEFieldMap("elevate", TurSEFieldType.STRING, "elevate", null));
		fieldMaps.put("title", new TurSEFieldMap("title", TurSEFieldType.STRING, "title", null));
		fieldMaps.put("author", new TurSEFieldMap("author", TurSEFieldType.STRING, "author", null));	
		//fieldMaps.put("date", new TurSEFieldMap("date", TurSEFieldType.STRING, "date", null)); //'%Y-%m-%d'
		fieldMaps.put("image", new TurSEFieldMap("image", TurSEFieldType.STRING, "image", null));
		fieldMaps.put("text", new TurSEFieldMap("text", TurSEFieldType.STRING, "text", null));
		fieldMaps.put("abstract", new TurSEFieldMap("abstract", TurSEFieldType.STRING, "abstract", null));
		fieldMaps.put("section", new TurSEFieldMap("section", TurSEFieldType.STRING, "section", null));
		fieldMaps.put("subsection", new TurSEFieldMap("subsection", TurSEFieldType.STRING, "subsection", null));
		fieldMaps.put("url", new TurSEFieldMap("url", TurSEFieldType.STRING, "url", null));
		fieldMaps.put("last_published", new TurSEFieldMap("last_published", TurSEFieldType.STRING, "lastPublished", null));
		fieldMaps.put("original_date", new TurSEFieldMap("original_date", TurSEFieldType.DATE, "originalDate", null));
		fieldMaps.put("sebna_tipo", new TurSEFieldMap("sebna_tipo", TurSEFieldType.STRING, "sebnaTipo", null));
		
		//Corrigir no broker
		fieldMaps.put("sebna_destaque", new TurSEFieldMap("sebna_destaque", TurSEFieldType.BOOL, "sebnaDestaque", new TurSEFieldRequired(true, false)));
		fieldMaps.put("sebna_nacional", new TurSEFieldMap("sebna_nacional", TurSEFieldType.BOOL, "sebnaNacional", null));
		fieldMaps.put("sebna_mobile", new TurSEFieldMap("sebna_mobile", TurSEFieldType.BOOL, "sebnaMobile", null));
		fieldMaps.put("sebna_tema", new TurSEFieldMap("sebna_tema", TurSEFieldType.STRING, "sebnaTema", null));
		fieldMaps.put("sebna_dtini_destaque", new TurSEFieldMap("sebna_dtini_destaque", TurSEFieldType.DATE, "sebnaDtiniDestaque", null));
		fieldMaps.put("sebna_dtfin_destaque", new TurSEFieldMap("sebna_dtfin_destaque", TurSEFieldType.DATE, "sebna:DtfinDestaque", null));
		
		//Corrigir no broker
		fieldMaps.put("sebna_qtd_visualizacao", new TurSEFieldMap("sebna_qtd_visualizacao", TurSEFieldType.INT, "sebnaQtdVisualizacao", null));
		fieldMaps.put("sebna_perfil", new TurSEFieldMap("sebna_perfil", TurSEFieldType.STRING, "sebnaPerfil", null));

		//Corrigir no broker
		fieldMaps.put("sebna_uf", new TurSEFieldMap("sebna_uf", TurSEFieldType.STRING, "sebnaUF", null));	
		fieldMaps.put("sebna_segmento", new TurSEFieldMap("sebna_segmento", TurSEFieldType.STRING, "sebnaSegmento", null));
		
		//Conferir se está montando Array
		fieldMaps.put("sebna_momento_startup", new TurSEFieldMap("sebna_momento_startup", TurSEFieldType.ARRAY, "sebnaMomentoStartup", null));
		
		//criar este atributo no broker
		fieldMaps.put("sebna:classtema", new TurSEFieldMap("sebna:classtema", TurSEFieldType.STRING, "sebnaClasstema", null));
		
		
	}
	
	
}