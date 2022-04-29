package com.viglet.turing.wem.beans;



import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.mappers.MappingDefinitions;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurAttrDefContext {
	private static final ContextLogger logger = ContextLogger.getLogger(TurAttrDefContext.class.getName());
	
	private ContentInstance contentInstance;
	private TuringTag turingTag;
	private IHandlerConfiguration iHandlerConfiguration;
	private MappingDefinitions mappingDefinitions;
	private AttributeData attributeData;

	public TurAttrDefContext(TurAttrDefContext turAttrDefContext) {
		this.setAttributeData(turAttrDefContext.getAttributeData());
		this.setContentInstance(turAttrDefContext.getContentInstance());
		this.setiHandlerConfiguration(turAttrDefContext.getiHandlerConfiguration());
		this.setMappingDefinitions(turAttrDefContext.getMappingDefinitions());
		this.setTuringTag(turAttrDefContext.getTuringTag());
	}

	public TurAttrDefContext(ContentInstance contentInstance, TuringTag turingTag,
			IHandlerConfiguration iHandlerConfiguration, MappingDefinitions mappingDefinitions) {
		this.setContentInstance(contentInstance);
		this.setiHandlerConfiguration(iHandlerConfiguration);
		this.setMappingDefinitions(mappingDefinitions);
		this.setTuringTag(turingTag);
	}

	public ContentInstance getContentInstance() {
		return contentInstance;
	}

	public void setContentInstance(ContentInstance contentInstance) {
		this.contentInstance = contentInstance;
	}

	public TuringTag getTuringTag() {
		return turingTag;
	}

	public void setTuringTag(TuringTag turingTag) {
		this.turingTag = turingTag;
	}

	public IHandlerConfiguration getiHandlerConfiguration() {
		return iHandlerConfiguration;
	}

	public void setiHandlerConfiguration(IHandlerConfiguration iHandlerConfiguration) {
		this.iHandlerConfiguration = iHandlerConfiguration;
	}

	public MappingDefinitions getMappingDefinitions() {
		return mappingDefinitions;
	}

	public void setMappingDefinitions(MappingDefinitions mappingDefinitions) {
		this.mappingDefinitions = mappingDefinitions;
	}

	public AttributeData getAttributeData() {
		if (attributeData != null)
			return attributeData;

		try {
			return contentInstance.getAttribute(turingTag.getSrcXmlName());
		} catch (ApplicationException e) {
			logger.error(e);
		}

		return null;
	}

	public void setAttributeData(AttributeData attributeData) {
		this.attributeData = attributeData;
	}
}
