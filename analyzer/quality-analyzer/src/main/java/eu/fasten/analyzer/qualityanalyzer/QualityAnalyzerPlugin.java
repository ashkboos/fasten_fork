/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.qualityanalyzer;

import eu.fasten.analyzer.qualityanalyzer.data.QAConstants;

import eu.fasten.core.plugins.KafkaPlugin;
import eu.fasten.core.plugins.DBConnector;

import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Collections;

import org.jooq.DSLContext;


public class QualityAnalyzerPlugin extends Plugin {

    public QualityAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class QualityAnalyzer implements KafkaPlugin, DBConnector {

        private final Logger logger = LoggerFactory.getLogger(QualityAnalyzer.class.getName());
        private String consumerTopic = "fasten.RapidPlugin.out";
        private MetadataUtils utils = null;

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            this.utils = new MetadataUtils(dslContexts);
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public void consume(String kafkaMessage) {

            logger.info("Consumed: " + kafkaMessage);

            var jsonRecord = new JSONObject(kafkaMessage);
            String forge = null;

            if (jsonRecord.has("payload")) {
                forge = jsonRecord
                        .getJSONObject("payload")
                        .getString("forge".replaceAll("[\\n\\t ]", ""));
            }

            logger.info("forge = " + forge);

            //TODO: what if forge = null? Throw an exception?

            if (forge != null) {
                utils.insertMetadataIntoDB(forge, jsonRecord);
            } else {
                logger.error("Could not extract forge from the message");
            }
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return null;
        }

        @Override
        public String name() {
            return QAConstants.QA_PLUGIN_NAME;
        }

        @Override
        public String description() {
            return "Consumes code metrics generated by Lizard from Kafka topic"
                    + " and populates callable metadata with this metrics.";
        }

        @Override
        public String version() {
            return QAConstants.QA_VERSION_NUMBER;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Throwable getPluginError() {
            return null;
        }

        @Override
        public void freeResource() {
            utils.freeResource();
        }
    }


}
