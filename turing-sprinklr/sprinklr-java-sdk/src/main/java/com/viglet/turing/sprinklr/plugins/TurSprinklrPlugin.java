package com.viglet.turing.sprinklr.plugins;


public interface TurSprinklrPlugin {
    /**
     * Retrieves the name associated with this plugin.
     *
     * @return the name of the plugin
     */
    String getName();
    /**
     * Retrieves the description of the plugin.
     *
     * @return a String representing the description of the plugin.
     */
    String getDescription();

}
