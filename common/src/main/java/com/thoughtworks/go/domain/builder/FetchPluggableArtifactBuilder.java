/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.domain.builder;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.thoughtworks.go.config.ArtifactStore;
import com.thoughtworks.go.domain.*;
import com.thoughtworks.go.domain.config.Configuration;
import com.thoughtworks.go.plugin.access.artifact.ArtifactExtension;
import com.thoughtworks.go.plugin.access.pluggabletask.TaskExtension;
import com.thoughtworks.go.util.GoConstants;
import com.thoughtworks.go.util.command.EnvironmentVariableContext;
import com.thoughtworks.go.util.command.TaggedStreamConsumer;
import com.thoughtworks.go.work.DefaultGoPublisher;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.lang.String.format;

public class FetchPluggableArtifactBuilder extends Builder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchPluggableArtifactBuilder.class);

    private final JobIdentifier jobIdentifier;
    private final File metadataFileDest;
    private ChecksumFileHandler checksumFileHandler;
    private ArtifactStore artifactStore;
    private Configuration configuration;
    private final String metadataFileLocationOnServer;

    public FetchPluggableArtifactBuilder(RunIfConfigs conditions, Builder cancelBuilder, String description,
                                         JobIdentifier jobIdentifier, ArtifactStore artifactStore, Configuration configuration,
                                         String source, File metadataFileDest, ChecksumFileHandler checksumFileHandler) {
        super(conditions, cancelBuilder, description);
        this.jobIdentifier = jobIdentifier;
        this.artifactStore = artifactStore;
        this.configuration = configuration;
        this.metadataFileDest = metadataFileDest;
        this.checksumFileHandler = checksumFileHandler;
        this.metadataFileLocationOnServer = source;
    }

    public void build(DefaultGoPublisher publisher, EnvironmentVariableContext environmentVariableContext, TaskExtension taskExtension, ArtifactExtension artifactExtension) {
        downloadMetadataFile(publisher);

        try {
            final String message = format("[%s] Fetching pluggable artifact using plugin %s.", GoConstants.PRODUCT_NAME, artifactStore.getPluginId());
            LOGGER.info(message);
            publisher.taggedConsumeLine(TaggedStreamConsumer.OUT, message);

            artifactExtension.fetchArtifact(artifactStore.getPluginId(), artifactStore, configuration, getMetadataFromFile(), agentWorkingDirectory());
        } catch (Exception e) {
            publisher.taggedConsumeLine(TaggedStreamConsumer.ERR, e.getMessage());
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String agentWorkingDirectory() {
        return metadataFileDest.getParentFile().getAbsolutePath();
    }

    private void downloadMetadataFile(DefaultGoPublisher publisher) {
        final FetchArtifactBuilder fetchArtifactBuilder = new FetchArtifactBuilder(conditions, getCancelBuilder(), getDescription(), jobIdentifier, metadataFileLocationOnServer, metadataFileDest.getName(), getHandler(), checksumFileHandler);

        publisher.fetch(fetchArtifactBuilder);
    }

    public FetchHandler getHandler() {
        return new FileHandler(metadataFileDest, metadataFileLocationOnServer);
    }

    public String metadataFileLocator() {
        return jobIdentifier.artifactLocator(metadataFileDest.getName());
    }

    private Map<String, Object> getMetadataFromFile() throws IOException {
        final String fileToString = FileUtils.readFileToString(metadataFileDest, StandardCharsets.UTF_8);
        LOGGER.debug(format("Reading metadata from file %s.", metadataFileDest.getAbsolutePath()));
        final Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        return new GsonBuilder().create().fromJson(fileToString, type);
    }

    public JobIdentifier getJobIdentifier() {
        return jobIdentifier;
    }

    @Override
    public BuildCommand buildCommand() {
        throw new UnsupportedOperationException("Not supported yet!");
    }
}
