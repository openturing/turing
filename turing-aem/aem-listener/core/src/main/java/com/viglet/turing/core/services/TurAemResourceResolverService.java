package com.viglet.turing.core.services;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;

public interface TurAemResourceResolverService {

    ResourceResolver getResourceResolver() throws LoginException;

}
