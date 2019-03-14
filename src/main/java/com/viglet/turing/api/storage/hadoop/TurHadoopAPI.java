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
import org.apache.hadoop.fs.FileStatus;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.commons.lang3.StringEscapeUtils;

import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import io.swagger.annotations.Api;


@RestController
@RequestMapping("/api/storage/hadoop")
@Api(tags = "Hadoop", description = "Hadoop API")
public class TurHadoopAPI {

	@Autowired
	TurSNSiteRepository turSNSiteRepository;

	/*@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<org.apache.hadoop.fs.Path> list() throws JSONException, IOException {

		try {
			UserGroupInformation ugi = UserGroupInformation.createRemoteUser("root");

			return ugi.doAs(new PrivilegedExceptionAction<List<org.apache.hadoop.fs.Path>>() {

				public List<org.apache.hadoop.fs.Path> run() throws Exception {
					Configuration conf = new Configuration();
					conf.set("fs.defaultFS", "hdfs://192.168.0.6:8020");
					conf.set("hadoop.job.ugi", "root");
					FileSystem fs = FileSystem.get(conf);

					List<org.apache.hadoop.fs.Path> turFileStatuses = new ArrayList<org.apache.hadoop.fs.Path>();
					
					FileStatus[] status = fs.listStatus(new org.apache.hadoop.fs.Path("/"));
					for (int i = 0; i < status.length; i++) {

						turFileStatuses.add(status[i].getPath());

					}
					return turFileStatuses;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}*/

	@GetMapping
	public TurChildPath turHadoopPath(@RequestParam String path) throws JSONException, IOException {
		try {
			UserGroupInformation ugi = UserGroupInformation.createRemoteUser("root");

			return ugi.doAs(new PrivilegedExceptionAction<TurChildPath>() {

				public TurChildPath run() throws Exception {
					Configuration conf = new Configuration();
					conf.set("fs.defaultFS", "hdfs://192.168.0.6:8020");
					conf.set("hadoop.job.ugi", "root");
					FileSystem fs = FileSystem.get(conf);
					String pathUnescape = StringEscapeUtils.unescapeHtml4(path);
					System.out.println("Teste");
					
					System.out.println(pathUnescape);
					List<org.apache.hadoop.fs.Path> turFileStatuses = new ArrayList<org.apache.hadoop.fs.Path>();
					FileStatus[] status = fs.listStatus(new org.apache.hadoop.fs.Path(pathUnescape));
					for (int i = 0; i < status.length; i++) {

						turFileStatuses.add(status[i].getPath());

					}
					TurChildPath turChildPath = new TurChildPath();
					turChildPath.setDir(turFileStatuses);
					return turChildPath;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}