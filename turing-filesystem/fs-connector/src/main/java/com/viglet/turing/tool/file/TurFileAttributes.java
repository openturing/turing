package com.viglet.turing.tool.file;

import java.io.File;

import org.apache.tika.metadata.Metadata;

public class TurFileAttributes {
	private File file;
	private String content;	
	private Metadata metadata;
	
	public File getFile() {
		return file;
	}
	public void setFile(File file) {
		this.file = file;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public Metadata getMetadata() {
		return metadata;
	}
	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}
	
	
}
