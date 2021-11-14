/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.plugins.storage.hadoop;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TurHDFSConnector {
	private static final Logger logger = LogManager.getLogger(TurHDFSConnector.class);
	public TurHDFSConnector() {
		super();
	}

	/**
	 * create a existing file from local filesystem to hdfs
	 * 
	 * @param source
	 * @param dest
	 * @param conf
	 * @throws IOException
	 */
	public void addFile(String source, String dest, Configuration conf) throws IOException {

		// Get the filename out of the file path
		String filename = source.substring(source.lastIndexOf('/') + 1, source.length());

		// Create the destination path including the filename.
		if (dest.charAt(dest.length() - 1) != '/') {
			dest = dest + "/" + filename;
		} else {
			dest = dest + filename;
		}

		// Check if the file already exists
		Path path = new Path(dest);
		try (FileSystem fileSystem = FileSystem.get(conf);
				FSDataOutputStream out = fileSystem.create(path);
				InputStream in = new BufferedInputStream(new FileInputStream(new File(source)))) {
			if (fileSystem.exists(path)) {
				logger.info("File {} already exists", dest);
				return;
			}

			// Create a new file and write data to it.

			byte[] b = new byte[1024];
			int numBytes = 0;
			while ((numBytes = in.read(b)) > 0) {
				out.write(b, 0, numBytes);
			}
		}
	}

	/**
	 * read a file from hdfs
	 * 
	 * @param file
	 * @param conf
	 * @throws IOException
	 */
	public void readFile(String file, Configuration conf) throws IOException {
		Path path = new Path(file);
		String filename = file.substring(file.lastIndexOf('/') + 1, file.length());

		try (FileSystem fileSystem = FileSystem.get(conf);
				FSDataInputStream in = fileSystem.open(path);
				OutputStream out = new BufferedOutputStream(new FileOutputStream(new File(filename)))) {
			if (!fileSystem.exists(path)) {
				return;
			}

			byte[] b = new byte[1024];
			int numBytes = 0;
			while ((numBytes = in.read(b)) > 0) {
				out.write(b, 0, numBytes);
			}
		}
	}

	/**
	 * delete a directory in hdfs
	 * 
	 * @param file
	 * @throws IOException
	 */
	public void deleteFile(String file, Configuration conf) throws IOException {
		try (FileSystem fileSystem = FileSystem.get(conf)) {
			Path path = new Path(file);
			if (!fileSystem.exists(path)) {
				return;
			}

			fileSystem.delete(new Path(file), true);
		}
	}

	/**
	 * create directory in hdfs
	 * 
	 * @param dir
	 * @throws IOException
	 */
	public void mkdir(String dir, Configuration conf) throws IOException {
		try (FileSystem fileSystem = FileSystem.get(conf)) {
			Path path = new Path(dir);
			if (fileSystem.exists(path)) {
				return;
			}
			fileSystem.mkdirs(path);
		}
	}
}
