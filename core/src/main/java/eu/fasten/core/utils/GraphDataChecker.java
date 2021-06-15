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

package eu.fasten.core.utils;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.dbconnectors.PostgresConnector;
import org.jooq.DSLContext;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.sql.SQLException;

@CommandLine.Command(name = "GraphDataChecker")
public class GraphDataChecker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GraphDataChecker.class);

    @CommandLine.Option(names = {"-gd", "--graph-db"},
            paramLabel = "Dir",
            description = "The directory of the RocksDB instance")
    String graphDbPath;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:fasten_java")
    String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    String dbUser;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new GraphDataChecker()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        RocksDao graphDb;
        DSLContext metadataDb;
        try {
            graphDb = new RocksDao(graphDbPath, true, false);
            metadataDb = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
        } catch (RocksDBException | SQLException e) {
            logger.error("Could not setup connections to the database:", e);
            return;
        }
        var packageVersionIds = metadataDb.select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS).fetch()
                .intoSet(PackageVersions.PACKAGE_VERSIONS.ID);
        logger.info("In total there are {} artifacts", packageVersionIds.size());
        var counter = 0;
        for (var packageVersionId : packageVersionIds) {
            DirectedGraph graphData;
            try {
                graphData = graphDb.getGraphData(packageVersionId);
                if (graphData == null) {
                    continue;
                }
            } catch (RocksDBException e) {
                continue;
            }
            try {
                var graphMetadata = graphDb.getGraphMetadata(packageVersionId, graphData);
                if (graphMetadata != null) {
                    counter++;
                    logger.info("Retrieved {} graph metadata", counter);
                }
            } catch (RocksDBException ignored) {
            }
        }
        logger.info("Finished counting - {}/{} artifacts are in the graph database", counter, packageVersionIds.size());
    }
}
