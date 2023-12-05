package com.viglet.turing.connector.aem.indexer.bean;

public class TurInsperModelJsonBean {
    private String templateName;
    private String fragmentPath;
    private TurInsperFragmentDataBean genericContentFragmentData;

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getFragmentPath() {
        return fragmentPath;
    }

    public void setFragmentPath(String fragmentPath) {
        this.fragmentPath = fragmentPath;
    }

    public TurInsperFragmentDataBean getGenericContentFragmentData() {
        return genericContentFragmentData;
    }

    public void setGenericContentFragmentData(TurInsperFragmentDataBean genericContentFragmentData) {
        this.genericContentFragmentData = genericContentFragmentData;
    }
}
