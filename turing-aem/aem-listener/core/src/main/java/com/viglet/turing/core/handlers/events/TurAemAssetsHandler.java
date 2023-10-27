package com.viglet.turing.core.handlers.events;

import com.day.cq.replication.Replicator;
import com.viglet.turing.core.handlers.TurAemEventHandler;
import com.viglet.turing.core.services.TurAemResourceResolverService;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

@Component(service = EventHandler.class,
        immediate = true,
        property = {
                Constants.SERVICE_DESCRIPTION + "=" + "Listen to the content fragments events.",
                EventConstants.EVENT_TOPIC + "=" + "com/day/cq/dam"
        })
public class TurAemAssetsHandler extends TurAemEventHandler {

    @Reference
    Replicator replicator;

    @Reference
    TurAemResourceResolverService resolverService;

    @Override
    protected Replicator getReplicator() {
        return replicator;
    }

    @Override
    protected TurAemResourceResolverService getResolverService() {
        return resolverService;
    }

    @Override
    protected String getPathAttributeName() {
        return "assetPath";
    }

    @Override
    protected String getPathPattern() {
        return "^/content/dam/maple-bear/events/";
    }

    @Override
    protected String getPagesPath() {
        return "/content/maple-bear/events/";
    }

    @Override
    protected String getTemplate() {
        return "/conf/maple-bear/settings/wcm/templates/maple-bear-events";
    }
}
