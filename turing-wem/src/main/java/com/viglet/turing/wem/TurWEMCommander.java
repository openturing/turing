/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.wem;

import ch.qos.logback.classic.Level;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.viglet.turing.wem.broker.indexer.TurWEMIndex;
import com.viglet.turing.wem.broker.indexer.TurWEMIndexer;
import com.viglet.turing.wem.config.GenericResourceHandlerConfiguration;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.index.IValidToIndex;
import com.viglet.turing.wem.mappers.MappingDefinitions;
import com.viglet.turing.wem.mappers.MappingDefinitionsProcess;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.*;
import com.vignette.as.client.common.ref.ContentTypeRef;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.common.ref.ObjectTypeRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.*;
import com.vignette.as.config.ConfigUtil;
import com.vignette.config.client.common.ConfigException;
import com.vignette.config.util.ConfigLog;
import com.vignette.util.VgnException;
import com.vignette.util.VgnIllegalArgumentException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Slf4j
public class TurWEMCommander {
    private static final JCommander jCommander = new JCommander();

    private static final String WORKING_DIR = "com.vignette.workingDir";
    private static final String STFL = "STFL";
    private static final String RCRD = "RCRD";
    private IHandlerConfiguration turingConfig = null;
    @Parameter(names = {"--working-dir",
            "-w"}, description = "The working directory where the vgncfg.properties file is located.", required = true)
    private String workingDir = null;

    @Parameter(names = {"--all", "-a"}, description = "Index all instances of all content types and object types.")
    private boolean allObjectTypes = false;

    @Parameter(names = {"--content-type",
            "-c"}, description = "The XML name of the content type or object type whose instances are to be indexed.")
    private String contentType = null;

    @Parameter(names = {"--guids",
            "-g"}, description = "The path to a file containing the GUID(s) of content instances or static files to be indexed.")
    private String guidFilePath = null;

    @Parameter(names = {"--siteName", "-s"}, description = "WEM site name.", required = true)
    private String siteName = "Sample";

    @Parameter(names = {"--page-size",
            "-z"}, description = "The page size. After processing a page the processed count is written to an offset file."
            + " This helps the indexer to resume from that page even after failure. ")
    private int pageSize = 500;

    @Parameter(names = "--debug", description = "Change the log level to debug", help = true)
    private boolean debug = false;

    @Parameter(names = "--help", description = "Print usage instructions", help = true)
    private boolean help = false;

    public static void main(String... argv) {
        TurWEMCommander main = new TurWEMCommander();

        jCommander.addObject(main);

        try {
            jCommander.parse(argv);
            if (main.help) {
                jCommander.usage();
                return;
            }
            jCommander.getConsole().println("Viglet Turing WEM Indexer Tool.");

            main.run();
        } catch (ParameterException e) {
            log.info("Error: {}", e.getLocalizedMessage());
            jCommander.usage();
        }

    }

    private void run() {
        String logLevel = debug ? "DEBUG" : "INFO";

        try {
            ConfigLog.initializeLogging("turing-wem.log", logLevel);
            if (debug)
                ((ch.qos.logback.classic.Logger) LoggerFactory.
                        getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.DEBUG);

            System.setProperty(WORKING_DIR, workingDir);
            ConfigUtil.setHasDataSource(false);
            ConfigUtil.setContainerType(ConfigUtil.CONTAINER_TYPE_SERVLET);

            turingConfig = new GenericResourceHandlerConfiguration();
            if (allObjectTypes) {
                runAllObjectTypes();
            } else if (contentType != null) {
                runByContentType();
            } else if (guidFilePath != null) {
                runByGuidList();
            }

        } catch (ConfigException exception) {
            if (log.isDebugEnabled())
                log.debug("Error into ConfigSpace configuration", exception);
        } catch (VgnException vgnException) {
            jCommander.getConsole().println("Logging does not started");
        } catch (Exception e) {
            log.error("Viglet Turing Index Error: ", e);

        }
    }

    private void runByContentType() throws ApplicationException, ConfigException, ValidationException {
        ObjectType objectType = ObjectType.findByName(contentType);
        if (objectType != null)
            this.indexByContentType(siteName, objectType);
    }

    private void runByGuidList()
            throws ValidationException, ApplicationException, ConfigException {
        ArrayList<String> contentInstances = new ArrayList<>();
        try (FileReader fr = new FileReader(guidFilePath); BufferedReader br = new BufferedReader(fr)) {

            String sCurrentLine;

            while ((sCurrentLine = br.readLine()) != null) {
                if (sCurrentLine.endsWith(STFL) || sCurrentLine.endsWith(RCRD))
                    contentInstances.add(sCurrentLine);

                if (contentInstances.size() != pageSize)
                    continue;
                if (!contentInstances.isEmpty()) {
                    this.indexGUIDList(contentInstances);
                    contentInstances = new ArrayList<>();
                }
            }
            if (!contentInstances.isEmpty())
                this.indexGUIDList(contentInstances);

        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void runAllObjectTypes() throws ApplicationException, ConfigException, ValidationException {
        IPagingList contentTypeIPagingList = ContentType.findAll();
        @SuppressWarnings("unchecked")
        List<Object> contentTypes = contentTypeIPagingList.asList();
        contentTypes.add(StaticFile.getTypeObjectTypeRef().getObjectType());

        jCommander.getConsole().println(
                String.format("Total number of Object Types: %d", TurWEMIndex.countCTDIntoMapping(turingConfig)));
        for (Object objectType : contentTypes) {
            ObjectType ot = (ObjectType) objectType;
            if (TurWEMIndex.isCTDIntoMapping(ot.getData().getName(), turingConfig)) {
                jCommander.getConsole().println(String.format("%n Retrieved Object Type: %s %s", ot.getData().getName(),
                        ot.getContentManagementId().toString()));
                this.indexByContentType(siteName, ot);
            }
        }
    }

    private void indexByContentType(String siteName, ObjectType objectType)
            throws ApplicationException {
        if (TurWEMIndex.isCTDIntoMapping(objectType.getData().getName(), turingConfig)) {
            int totalPages = 0;
            IPagingList results = null;
            int totalEntries;
            try {
                TurWEMIndexer.indexDeleteByType(siteName, objectType.getData().getName(), turingConfig);
                MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(turingConfig);
                RequestParameters rp = new RequestParameters();
                rp.setTopRelationOnly(false);

                AsObjectType aot = AsObjectType.getInstance(new ObjectTypeRef((ManagedObject) objectType));
                IValidToIndex instance = mappingDefinitions.validToIndex(objectType, turingConfig);
                if (aot.isStaticFile()) {
                    results = queryStaticFilesList(rp, instance);
                } else {
                    results = queryContentInstanceList(objectType, rp, instance);
                }
                totalEntries = results.size();
                jCommander.getConsole().println(String.format("Number of Content Instances of type %s %s = %d",
                        objectType.getData().getName(), objectType.getContentManagementId().toString(), totalEntries));
                totalPages = totalEntries > 0 ? (totalEntries + pageSize - 1) / pageSize : totalEntries / pageSize;

            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            if (results != null) {
                indexByContentTypeProcess(totalPages, results);
            }
        } else {
            jCommander.getConsole().println(String.format("%s type is not configured in CTD Mapping XML file.",
                    objectType.getData().getName()));
        }

    }

    private IPagingList queryContentInstanceList(ObjectType objectType, RequestParameters rp, IValidToIndex instance)
            throws Exception {

        IPagingList results;
        ContentInstanceWhereClause clause = new ContentInstanceWhereClause();
        ContentInstanceDBQuery query = new ContentInstanceDBQuery(new ContentTypeRef(objectType.getId()));
        if (instance != null)
            instance.whereToValid(clause, turingConfig);

        query.setWhereClause(clause);
        results = QueryManager.execute(query, (AsObjectRequestParameters) rp);
        return results;
    }

    private IPagingList queryStaticFilesList(RequestParameters rp, IValidToIndex instance) throws Exception {
        IPagingList results;
        StaticFileWhereClause clause = new StaticFileWhereClause();
        StaticFileDBQuery query = new StaticFileDBQuery();
        if (instance != null)
            instance.whereToValid(clause, turingConfig);
        query.setWhereClause(clause);
        results = QueryManager.execute(query, (AsObjectRequestParameters) rp);
        return results;
    }

    private void indexByContentTypeProcess(int totalPages, IPagingList results) throws ApplicationException {
        Iterator<?> it = results.pageIterator(pageSize);
        int currentPage = 1;
        if (it != null) {
            while (it.hasNext()) {
                List<?> managedObjects = (List<?>) it.next();
                jCommander.getConsole()
                        .println(String.format("Processing Page %d of %d pages", currentPage++, totalPages));
                long start = System.currentTimeMillis();
                try {
                    HashSet<ManagedObjectVCMRef> validGuids = new HashSet<>();
                    HashMap<String, ManagedObject> objectMap = new HashMap<>(
                            managedObjects.size());
                    for (Object object : managedObjects) {
                        ManagedObject mo = (ManagedObject) object;
                        if (mo instanceof ContentItem) {
                            ContentItem ci = (ContentItem) mo;
                            if (ci.getChannelAssociations() == null || ci.getChannelAssociations().length == 0)
                                continue;

                        }
                        objectMap.put(mo.getContentManagementId().getId(), mo);
                        validGuids.add(mo.getContentManagementId());
                    }
                    ManagedObjectVCMRef[] guids = null;
                    if (!validGuids.isEmpty())
                        guids = validGuids.toArray(new ManagedObjectVCMRef[0]);

                    jCommander.getConsole()
                            .println(String.format("Processing the registration of %d assets", validGuids.size()));
                    if (guids != null) {
                        this.indexContentInstances(guids, objectMap);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(),e);
                }
                long elapsed = System.currentTimeMillis() - start;
                jCommander.getConsole()
                        .println(String.format("%d items processed in %dms", managedObjects.size(), elapsed));

            }
        }
    }

    private void indexGUIDList(List<String> guids)
            throws ValidationException, ApplicationException {
        jCommander.getConsole().println(String.format("Processing a total of %d GUID Strings", guids.size()));

        ArrayList<ManagedObjectVCMRef> validGuids = new ArrayList<>();
        for (String guid : guids) {
            if (guid != null && !guid.isEmpty()) {
                try {
                    ManagedObjectVCMRef ref = new ManagedObjectVCMRef(guid);
                    validGuids.add(ref);
                } catch (VgnIllegalArgumentException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        ManagedObjectVCMRef[] managedObjectVCMRefs = null;
        if (!validGuids.isEmpty())
            managedObjectVCMRefs = validGuids.toArray(new ManagedObjectVCMRef[0]);

        if (managedObjectVCMRefs == null)
            log.error("No GUIDs");
        else {
            RequestParameters params = new RequestParameters();
            params.setTopRelationOnly(false);
            IPagingList managedObjects = ManagedObject.findByContentManagementIds(managedObjectVCMRefs, params);
            List<?> moList = managedObjects.asList();
            HashMap<String, ManagedObject> objectMap = new HashMap<>(moList.size());
            for (Object object : moList) {
                ManagedObject mo = (ManagedObject) object;
                objectMap.put(mo.getContentManagementId().getId(), mo);
            }
            jCommander.getConsole()
                    .println(String.format("Processing the registration of %d assets", managedObjects.size()));
            this.indexContentInstances(managedObjectVCMRefs, objectMap);
        }
    }

    private void indexContentInstances(ManagedObjectVCMRef[] refs, HashMap<String, ?> objects) {
        for (ManagedObjectVCMRef ref : refs) {
            ManagedObject mo = (ManagedObject) objects.get(ref.getId());
            if (mo instanceof ContentInstance || mo instanceof Channel) {
                if (log.isDebugEnabled())
                    log.debug(String.format("Attempting to index the Content Instance: %s",
                            mo.getContentManagementId().getId()));
                TurWEMIndexer.indexCreate(mo, turingConfig, TuringUtils.getSiteNameFromContentInstance(mo, turingConfig));
            }
        }
    }
}
