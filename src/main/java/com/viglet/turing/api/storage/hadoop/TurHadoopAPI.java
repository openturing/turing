package com.viglet.turing.api.storage.hadoop;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileStatus;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.apache.commons.lang3.StringEscapeUtils;

import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

@Component
@Path("storage/hadoop")
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

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurChildPath getPath(@QueryParam("path") String path) throws JSONException, IOException {
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