package com.viglet.turing.jersey;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.viglet.turing.api.entity.TurNLPEntityAPI;
import com.viglet.turing.api.entity.TurNLPEntityTermAPI;
import com.viglet.turing.api.ml.TurMLInstanceAPI;
import com.viglet.turing.api.ml.TurMLVendorAPI;
import com.viglet.turing.api.ml.category.TurMLCategoryAPI;
import com.viglet.turing.api.system.TurLocaleAPI;
import com.viglet.turing.api.ml.data.TurMLDataAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupCategoryAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupDataAPI;
import com.viglet.turing.api.ml.data.group.TurMLDataGroupSentenceAPI;
import com.viglet.turing.api.ml.data.sentence.TurMLDataSentenceAPI;
import com.viglet.turing.api.ml.model.TurMLModelAPI;
import com.viglet.turing.api.nlp.TurNLPInstanceAPI;
import com.viglet.turing.api.nlp.TurNLPVendorAPI;
import com.viglet.turing.api.otca.af.TurOTCAAutorityFileAPI;
import com.viglet.turing.api.otsn.broker.TurOTSNBrokerAPI;
import com.viglet.turing.api.otsn.search.TurOTSNSearchAPI;
import com.viglet.turing.api.se.TurSEInstanceAPI;
import com.viglet.turing.api.se.TurSEVendorAPI;
import com.viglet.turing.api.sn.TurSNSiteAPI;

import javax.ws.rs.ApplicationPath;

@Profile("production")
@Component
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {

	public JerseyConfig() {
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
		register(TurMLDataSentenceAPI.class);
		register(TurMLModelAPI.class);
		register(TurNLPInstanceAPI.class);
		register(TurNLPVendorAPI.class);
		register(TurOTSNBrokerAPI.class);
		register(TurOTSNSearchAPI.class);
		register(TurSEInstanceAPI.class);
		register(TurSEVendorAPI.class);
		register(TurSNSiteAPI.class);
	}
}
