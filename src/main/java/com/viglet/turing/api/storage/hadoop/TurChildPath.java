package com.viglet.turing.api.storage.hadoop;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TurChildPath {
	List<org.apache.hadoop.fs.Path> dir;

	public List<org.apache.hadoop.fs.Path> getDir() {
		return dir;
	}

	public void setDir(List<org.apache.hadoop.fs.Path> dir) {
		this.dir = dir;
	}

	
	
}
