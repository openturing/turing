package com.viglet.turing.aem.service.core.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.viglet.turing.aem.service.core.rest.bean.TurAemSiteMapItem;
import org.apache.http.HttpStatus;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.jcr.contentloader.ContentTypeUtil;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.adobe.aemds.guide.utils.JcrResourceConstants.CQ_PAGE;
import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;

/**
 * Turing AEM Sitemap
 *
 * @since 0.3.7
 */
@Component(service = {Servlet.class})
@SlingServletPaths(value = {"/bin/turing-sitemap"})
@ServiceDescription("Turing AEM Sitemap")
public class TurAemCrawlerServlet extends SlingAllMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(TurAemCrawlerServlet.class);
    private static final long serialVersionUID = 1L;
    protected void doGet(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) {
        String suffix = req.getRequestPathInfo().getSuffix();
        List<TurAemSiteMapItem> items = new ArrayList<>();
        ResourceResolver resolver = req.getResourceResolver();
        Session session = resolver.adaptTo(Session.class);
        if (session != null && suffix != null) {
            try {
                Node node = session.getNode(suffix);
                getNode(node, items);
            } catch (RepositoryException e) {
                throw new RuntimeException(e);
            }
        }
        this.response(resp, items);
    }
    private void getNode(Node node, List<TurAemSiteMapItem> items) {

        try {
            if (node.hasNodes() && (node.getPath().startsWith("/content") || node.getPath().equals("/"))) {
                NodeIterator nodeIterator = node.getNodes();
                while (nodeIterator.hasNext()) {
                    Node nodeChild = nodeIterator.nextNode();
                    if (hasContentType(nodeChild, CQ_PAGE)) {
                        AemPage aemPage = new AemPage(nodeChild);
                        items.add(new TurAemSiteMapItem(aemPage.getUrl()));
                    }
                }
            }
        } catch (RepositoryException e) {
            log.error(e.getMessage(), e);
        }
    }
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    private <T> T response(SlingHttpServletResponse resp, int status, T object) {
        resp.addHeader(DavConstants.HEADER_CONTENT_TYPE, ContentTypeUtil.TYPE_JSON);
        resp.setStatus(status);
        try {
            resp.getWriter().print(new ObjectMapper().writeValueAsString(object));
            resp.getWriter().flush();
            return object;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private <T> T response(SlingHttpServletResponse resp, T object) {
        return response(resp, HttpStatus.SC_OK, object);
    }

    private static boolean hasContentType(Node nodeChild, String primaryType)
            throws RepositoryException {
        return primaryType != null && nodeChild.getProperty(JCR_PRIMARYTYPE).getString().equals(primaryType);
    }
}