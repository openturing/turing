/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.api.storage.hadoop;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/storage/hadoop")
@Tag(name = "Hadoop", description = "Hadoop API")
public class TurHadoopAPI {
	private static final Log logger = LogFactory.getLog(TurHadoopAPI.class);
	@Autowired
	TurSNSiteRepository turSNSiteRepository;

	@GetMapping
	public TurChildPath turHadoopPath(@RequestParam String path) {

		UserGroupInformation ugi = UserGroupInformation.createRemoteUser("root");

		try {
			return ugi.doAs(new PrivilegedExceptionAction<TurChildPath>() {

				public TurChildPath run() throws Exception {
					Configuration conf = new Configuration();
					conf.set("fs.defaultFS", "hdfs://192.168.0.6:8020");
					conf.set("hadoop.job.ugi", "root");
					FileSystem fs = FileSystem.get(conf);
					String pathUnescape = StringEscapeUtils.unescapeHtml4(path);
					logger.info(pathUnescape);
					List<Path> turFileStatuses = new ArrayList<>();
					FileStatus[] status = fs.listStatus(new Path(pathUnescape));
					for (int i = 0; i < status.length; i++) {
						turFileStatuses.add(status[i].getPath());
					}
					TurChildPath turChildPath = new TurChildPath();
					turChildPath.setDir(turFileStatuses);
					return turChildPath;
				}
			});
		} catch (InterruptedException e) {
			logger.error(e);
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			logger.error(e);
		}

		return null;
	}

}