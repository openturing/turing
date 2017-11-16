package com.viglet.turing.jersey;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.api.TurAPI;
import com.viglet.turing.api.entity.TurNLPEntityAPI;
import com.viglet.turing.api.entity.TurNLPEntityTermAPI;
import com.viglet.turing.api.filter.TurCORSFilter;
import com.viglet.turing.api.ml.TurMLInstanceAPI;
import com.viglet.turing.api.ml.TurMLVendorAPI;
import com.viglet.turing.api.ml.category.TurMLCategoryAPI;
import com.viglet.turing.api.system.TurLocaleAPI;
import com.viglet.turing.api.ml.data.TurMLDataAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupCategoryAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupDataAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupModelAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupSentenceAPI;
import com.viglet.turing.api.ml.data.sentence.TurMLDataSentenceAPI;
import com.viglet.turing.api.ml.model.TurMLModelAPI;
import com.viglet.turing.api.nlp.TurNLPInstanceAPI;
import com.viglet.turing.api.nlp.TurNLPVendorAPI;
import com.viglet.turing.api.otca.af.TurOTCAAutorityFileAPI;
import com.viglet.turing.api.otsn.broker.TurOTSNBrokerAPI;
import com.viglet.turing.api.se.TurSEInstanceAPI;
import com.viglet.turing.api.se.TurSEVendorAPI;
import com.viglet.turing.api.sn.TurSNImportAPI;
import com.viglet.turing.api.sn.TurSNSiteAPI;
import com.viglet.turing.api.sn.TurSNSiteFieldAPI;
import com.viglet.turing.api.sn.TurSNSiteFieldExtAPI;
import com.viglet.turing.api.sn.TurSNSiteSearchAPI;
import com.viglet.turing.api.storage.hadoop.TurHadoopAPI;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

@Profile("production")
@Component
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {
	@Autowired
	public JerseyConfig(ObjectMapper objectMapper) {
		// register endpoints
		packages("com.shengwang.demo");
		// register jackson for json
		register(new ObjectMapperContextResolver(objectMapper));
		register(TurCORSFilter.class);
		register(TurHadoopAPI.class);
		register(TurAPI.class);
		register(MultiPartFeature.class);
		register(TurOTCAAutorityFileAPI.class);
		register(TurNLPEntityAPI.class);
		register(TurNLPEntityTermAPI.class);
		register(TurMLInstanceAPI.class);
		register(TurMLVendorAPI.class);
		register(TurMLCategoryAPI.class);
		register(TurLocaleAPI.class);
		register(TurMLDataAPI.class);
		register(TurMLDataGroupAPI.class);
		register(TurMLDataGroupCategoryAPI.class);
		register(TurMLDataGroupDataAPI.class);
		register(TurMLDataGroupSentenceAPI.class);
		register(TurMLDataGroupModelAPI.class);
		register(TurMLDataSentenceAPI.class);
		register(TurMLModelAPI.class);
		register(TurNLPInstanceAPI.class);
		register(TurNLPVendorAPI.class);
		register(TurOTSNBrokerAPI.class);
		register(TurSEInstanceAPI.class);
		register(TurSEVendorAPI.class);
		register(TurSNSiteAPI.class);
		register(TurSNImportAPI.class);
		register(TurSNSiteFieldAPI.class);
		register(TurSNSiteFieldExtAPI.class);
		register(TurSNSiteSearchAPI.class);
	}

	@Provider
	public static class ObjectMapperContextResolver implements ContextResolver<ObjectMapper> {

		private final ObjectMapper mapper;

		public ObjectMapperContextResolver(ObjectMapper mapper) {
			this.mapper = mapper;
		}

		@Override
		public ObjectMapper getContext(Class<?> type) {
			return mapper;
		}
	}
}
