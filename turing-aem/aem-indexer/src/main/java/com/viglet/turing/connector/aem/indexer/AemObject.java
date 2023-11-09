package com.viglet.turing.connector.aem.indexer;

import com.google.gson.Gson;
import com.viglet.turing.connector.aem.indexer.bean.TurAEMPageModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.apache.jackrabbit.JcrConstants.*;
@Getter
@Slf4j
public class AemObject {
	private Calendar lastModified;
	private Calendar createdDate;
	private boolean contentFragment = false;
	private boolean delivered = false;
	private String type;
	private String path;
	private String url;
	private String model;
	private JSONObject node;
	private JSONObject jcrContentNode;
	private String language;
	private String title;
	private final Map<String, Object> attributes = new HashMap<>();

	public AemObject(Node node) {
		this(node, null);
	}

	public static final String CONTENT_FRAGMENT = "contentFragment";
	public static final String CQ_IS_DELIVERED = "cq:isDelivered";
	public static final String CQ_LAST_MODIFIED = "cq:lastModified";
	public static final String CQ_MODEL = "cq:model";
	public AemObject(JSONObject jcrContentNode, TurAEMPageModel turAEMPageModel) {
		SimpleDateFormat formatter = new SimpleDateFormat("E MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH);

		Calendar lastModifiedCalendar = Calendar.getInstance();
		Calendar createdDateCalendar = Calendar.getInstance();
		try {
			lastModifiedCalendar.setTime(formatter.parse(jcrContentNode.getString("cq:lastModified")));
			createdDateCalendar.setTime(formatter.parse(jcrContentNode.getString("jcr:created")));
		} catch (ParseException e) {
			log.error(e.getMessage(), e);
		}
		this.delivered = jcrContentNode.has("cq:isDelivered") && jcrContentNode.getBoolean("cq:isDelivered");
		this.language = turAEMPageModel.getLanguage();
		this.title = jcrContentNode.has("jcr:title")?jcrContentNode.getString("jcr:title"): "";
		this.lastModified = lastModifiedCalendar;
		this.createdDate = createdDateCalendar;
		this.path = turAEMPageModel.getPath();
		this.url = turAEMPageModel.getPath() + ".html";
		this.type = turAEMPageModel.getType();
		this.jcrContentNode = jcrContentNode;
		this.node = jcrContentNode;
	}
	public AemObject(Node node, String dataPath) {
		try {
			this.node = new JSONObject(new Gson().toJson(node));
			this.path = node.getPath();
			this.url = node.getPath() + ".html";
			type = node.getProperty(JCR_PRIMARYTYPE).getString();
			jcrContentNode = new JSONObject(new Gson().toJson(node.getNode(JCR_CONTENT)));
			if (TurAemUtils.hasProperty(jcrContentNode,CQ_LAST_MODIFIED))
				lastModified = (Calendar) jcrContentNode.get(CQ_LAST_MODIFIED);
			if (lastModified == null && TurAemUtils.hasProperty(jcrContentNode, JCR_LASTMODIFIED)) {
				lastModified = (Calendar) jcrContentNode.get(JCR_LASTMODIFIED);
			}
			if (TurAemUtils.hasProperty(jcrContentNode, CONTENT_FRAGMENT)) {
				contentFragment = jcrContentNode.getBoolean(CONTENT_FRAGMENT);
			}
			if (TurAemUtils.hasProperty(jcrContentNode, CQ_IS_DELIVERED)) {
				delivered = jcrContentNode.getBoolean(CQ_IS_DELIVERED);
			}
			if (TurAemUtils.hasProperty(this.node,JCR_CREATED))
				createdDate = node.getProperty(JCR_CREATED).getDate();
			JSONObject jcrDataRootNode = jcrContentNode.getJSONObject("data");
			if (TurAemUtils.hasProperty(jcrDataRootNode, CQ_MODEL)) {
				model = jcrDataRootNode.getString(CQ_MODEL);
			}
			if (dataPath != null) {
				attributes.putAll(jcrContentNode.getJSONObject(dataPath).toMap());
			}
		} catch (RepositoryException e) {
			log.error(e.getMessage(), e);
		}
    }
}
