/*
 * Copyright (C) 2021 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.aem.core.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;

import com.day.cq.tagging.Tag;
import com.day.cq.wcm.api.Page;

public final class TurAemUtils {
	private TurAemUtils() {
	}

	public static String[] getPageTags(Resource pageContent) {
		Page page = pageContent.getParent().adaptTo(Page.class);
		Tag tags[] = page.getTags();
		String tagsArray[] = new String[tags.length];
		for (int i = 0; i < tags.length; i++) {
			Tag tag = tags[i];
			tagsArray[i] = tag.getTitle();
		}
		return tagsArray;
	}

	public static String solrDate(Calendar cal) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ss");
		return dateFormat.format(cal.getTime()) + "Z";
	}

	public static String checkNull(String property) {
		if (StringUtils.isEmpty(property)) {
			return "";
		}
		return property;

	}

}
