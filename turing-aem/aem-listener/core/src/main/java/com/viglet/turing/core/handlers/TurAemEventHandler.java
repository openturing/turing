package com.viglet.turing.core.handlers;

import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationOptions;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;

import com.viglet.turing.core.services.TurAemResourceResolverService;
import com.viglet.turing.core.utils.TurAemJCRUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static com.day.cq.commons.jcr.JcrConstants.JCR_CONTENT;
import static com.day.cq.commons.jcr.JcrConstants.JCR_TITLE;
import static com.day.cq.tagging.TagConstants.PN_TAGS;

@Slf4j
public abstract class TurAemEventHandler implements EventHandler {

    protected String eventAction;
    protected String assetPath;
    protected String pageName;

    protected abstract Replicator getReplicator();
    protected abstract TurAemResourceResolverService getResolverService();

    protected abstract String getPathAttributeName();
    protected abstract String getPathPattern();
    protected abstract String getPagesPath();
    protected abstract String getTemplate();

    @Override
    public void handleEvent(final Event event) {
        try {
            String eventType = event.getProperty("type").toString();
            assetPath = event.getProperty(getPathAttributeName()).toString();

            if (!isValidEvent(eventType)) return;

            ResourceResolver resolver = getResolverService().getResourceResolver();
            PageManager pageManager = resolver.adaptTo(PageManager.class);

            String contentPath = assetPath.replaceFirst(getPathPattern(), "");
            pageName = contentPath.substring(contentPath.lastIndexOf("/") + 1);

            switch (eventType) {
                case "ContentFragment":
                    if(event.getProperty("action").toString().equals("cf_editing_finished"))
                        handleAssetVersioned(pageManager, resolver);
                    break;
                case "METADATA_UPDATED":
                    handleAssetVersioned(pageManager, resolver);
                    break;
                case "ASSET_PUBLISHED":
                    handleAssetReplicated(resolver, ReplicationActionType.ACTIVATE);
                    break;
                case "ASSET_REMOVED":
                    handleAssetRemoved(pageManager, resolver);
                    break;
                default:
                    log.info("The watched event does not have a scheduled action.");
            }
        } catch (LoginException e) {
            log.error("Error in Posts Publication EventHandler main Method. {}", e.getMessage());
        }
    }

    protected boolean isValidEvent(String eventType) {
        return (Objects.equals(eventType, "ContentFragment") ||
                Objects.equals(eventType, "METADATA_UPDATED") ||
                Objects.equals(eventType, "ASSET_PUBLISHED") ||
                Objects.equals(eventType, "ASSET_REMOVED")) &&
                Pattern.matches(getPathPattern().concat(".*"), assetPath);
    }

    protected void handleAssetVersioned(PageManager pageManager, ResourceResolver resolver) {
        try {
            Session session = resolver.adaptTo(Session.class);
            Page currentPage = pageManager.getPage(getPagesPath().concat(pageName));
            Node nodeContent = Objects.requireNonNull(resolver.getResource(assetPath + "/jcr:content")).adaptTo(Node.class);

            if (currentPage != null) {
                mergeTags(nodeContent, Objects.requireNonNull(currentPage.adaptTo(Node.class)).getNode(JCR_CONTENT), session);
                return;
            }

            Page postPage = pageManager.create(getPagesPath(), pageName, getTemplate(), TurAemJCRUtils.getContent(nodeContent, JCR_TITLE));
            Node pageNode = Objects.requireNonNull(postPage.adaptTo(Node.class)).getNode(JCR_CONTENT);
            pageNode.setProperty("associatedContent", assetPath);
            mergeTags(nodeContent, pageNode, session);
        } catch (RepositoryException | WCMException e) {
            log.error("Error in createPage EventHandler Method. {}", e.getMessage());
        }
    }

    protected void handleAssetReplicated(ResourceResolver resolver, ReplicationActionType replicationType) {
        try {
            String pagePath = getPagesPath().concat(pageName);

            Session session = resolver.adaptTo(Session.class);
            ReplicationOptions replicationOptions = new ReplicationOptions();
            replicationOptions.setSynchronous(true);

            getReplicator().replicate(session, replicationType, pagePath, replicationOptions);
        } catch (ReplicationException e) {
            log.error("Error in publishPage EventHandler Method. {}", e.getMessage());
        }
    }

    protected void handleAssetRemoved(PageManager pageManager, ResourceResolver resolver) {
        try {
            String pagePath = getPagesPath().concat(pageName);
            Page postPage = pageManager.getPage(pagePath);

            handleAssetReplicated(resolver, ReplicationActionType.DELETE);
            pageManager.delete(postPage, false);
        } catch (WCMException e) {
            log.error("Error in deletePage EventHandler Method. {}", e.getMessage());
        }
    }

    protected void mergeTags(Node nodeContent, Node nodePage, Session session) {
        try {
            Set<String> mergedSet = new HashSet<>();
            Node contentData = nodeContent.getNode("data/master");
            Node contentMetadata = nodeContent.getNode("metadata");

            mergedSet.addAll(TurAemJCRUtils.getValues(contentData, PN_TAGS));
            mergedSet.addAll(TurAemJCRUtils.getValues(contentMetadata, PN_TAGS));
            String[] mergedArray = mergedSet.toArray(new String[0]);

            contentData.setProperty(PN_TAGS, mergedArray);
            contentMetadata.setProperty(PN_TAGS, mergedArray);
            nodePage.setProperty(PN_TAGS, mergedArray);

            session.save();
        } catch (RepositoryException e) {
            log.error("Error in mergeTags EventHandler Method. {}", e.getMessage());
        }
    }
}

