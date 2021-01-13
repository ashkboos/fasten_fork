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

package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.api.StitchingApiService;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.DatabaseMerger;
import eu.fasten.core.utils.FastenUriUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StitchingApiServiceImpl implements StitchingApiService {

    @Override
    public ResponseEntity<String> resolveCallablesToUris(List<Long> gidList) {
        var fastenUris = KnowledgeBaseConnector.kbDao.getFullFastenUris(gidList);
        var json = new JSONObject();
        fastenUris.forEach((key, value) -> json.put(String.valueOf(key), value));
        var result = json.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getCallablesMetadata(List<String> fullFastenUris, boolean allAttributes, List<String> attributes) {
        Map<String, List<String>> packageVersionUris;
        try {
            packageVersionUris = fullFastenUris.stream().map(FastenUriUtils::parseFullFastenUri).collect(Collectors.toMap(
                    x -> x.get(0) + "!" + x.get(1) + "$" + x.get(2),
                    y -> List.of(y.get(3)),
                    (x, y) -> {
                        var z = new ArrayList<String>();
                        z.addAll(x);
                        z.addAll(y);
                        return z;
                    }));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        var metadataMap = new HashMap<String, JSONObject>(fullFastenUris.size());
        for (var artifact : packageVersionUris.keySet()) {
            var forge = artifact.split("!")[0];
            var forgelessArtifact = Arrays.stream(artifact.split("!")).skip(1).collect(Collectors.joining("!"));
            var packageName = forgelessArtifact.split("\\$")[0];
            var version = forgelessArtifact.split("\\$")[1];
            var partialUris = packageVersionUris.get(artifact);
            var uriMetadataMap = KnowledgeBaseConnector.kbDao.getCallablesMetadataByUri(forge, packageName, version, partialUris);
            if (uriMetadataMap == null) {
                return new ResponseEntity<>("Could not find one the FASTEN URIs for " + artifact, HttpStatus.NOT_FOUND);
            }
            metadataMap.putAll(uriMetadataMap);
        }
        var json = new JSONObject();
        for (var entry : metadataMap.entrySet()) {
            var neededMetadata = new JSONObject();
            if (!allAttributes) {
                for (var attribute : entry.getValue().keySet()) {
                    if (attributes.contains(attribute)) {
                        neededMetadata.put(attribute, entry.getValue().get(attribute));
                    }
                }
            } else {
                neededMetadata = entry.getValue();
            }
            json.put(entry.getKey(), neededMetadata);
        }
        var result = json.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> resolveMultipleDependencies(List<String> mavenCoordinates) {
        var revisions = mavenCoordinates.stream().map(c -> {
            var groupId = c.split(Constants.mvnCoordinateSeparator)[0];
            var artifactId = c.split(Constants.mvnCoordinateSeparator)[1];
            var version = c.split(Constants.mvnCoordinateSeparator)[2];
            var id = KnowledgeBaseConnector.kbDao.getPackageVersionID(groupId + Constants.mvnCoordinateSeparator + artifactId, version);
            return new Revision(id, groupId, artifactId, version, new Timestamp(-1));
        }).collect(Collectors.toSet());
        var virtualNode = KnowledgeBaseConnector.graphResolver.addVirtualNode(revisions);
        var depSet = KnowledgeBaseConnector.graphResolver.resolveDependencies(virtualNode, KnowledgeBaseConnector.dbContext, true);
        KnowledgeBaseConnector.graphResolver.removeVirtualNode(virtualNode);
        var jsonArray = new JSONArray();
        depSet.stream().map(r -> {
            var json = new JSONObject();
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                    r.artifactId.charAt(0), r.artifactId, r.artifactId, r.groupId, r.version);
            json.put(String.valueOf(r.id), url);
            return json;
        }).forEach(jsonArray::put);
        var result = jsonArray.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getDirectedGraph(long packageVersionId, boolean needStitching, long timestamp) {
        DirectedGraph graph;
        if (needStitching) {
            var mavenCoordinate = KnowledgeBaseConnector.kbDao.getMavenCoordinate(packageVersionId);
            var groupId = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[0];
            var artifactId = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[1];
            var version = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[2];
            var depSet = KnowledgeBaseConnector.graphResolver.resolveDependencies(groupId,
                    artifactId, version, timestamp, KnowledgeBaseConnector.dbContext, true);
            var depIds = depSet.stream().map(r -> r.id).collect(Collectors.toSet());
            var databaseMerger = new DatabaseMerger(depIds, KnowledgeBaseConnector.dbContext, KnowledgeBaseConnector.graphDao);
            graph = databaseMerger.mergeWithCHA(packageVersionId);
        } else {
            try {
                graph = KnowledgeBaseConnector.graphDao.getGraphData(packageVersionId);
            } catch (RocksDBException e) {
                return new ResponseEntity<>("Could not retrieve callgraph from the graph database",
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
            if (graph == null) {
                return new ResponseEntity<>("Callgraph not found in the graph database", HttpStatus.NOT_FOUND);
            }
        }
        var json = new JSONObject();
        var nodesJson = new JSONArray();
        graph.nodes().stream().forEach(nodesJson::put);
        var edgesJson = new JSONArray();
        graph.edgeSet().stream().map(e -> new long[]{e.firstLong(), e.secondLong()}).forEach(edgesJson::put);
        json.put("nodes", nodesJson);
        json.put("edges", edgesJson);
        var result = json.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getTransitiveVulnerabilities(String package_name, String version) {
        // TODO: dummy data; implement actual stitching response of transitive vulnerabilities.
        return new ResponseEntity<>(
                "[{\"vulnerability\":\"https://nvd.nist.gov/vuln/detail/CVE-2019-11777\",\"path\":[{\"id\":0,\"fastenUri\":\"test_uri\",\"className\":\"ClassA\",\"methodName\":\"MethodA()\"},{\"id\":1,\"fastenUri\":\"test_uri1\",\"className\":\"ClassB\",\"methodName\":\"MethodB()\"},{\"id\":2,\"fastenUri\":\"test_uri2\",\"className\":\"ClassC\",\"methodName\":\"MethodC()\"}]},{\"vulnerability\":\"https://nvd.nist.gov/vuln/detail/CVE-2019-11777\",\"path\":[{\"id\":0,\"fastenUri\":\"test_uri\",\"className\":\"ClassJ\",\"methodName\":\"MethodA()\"},{\"id\":1,\"fastenUri\":\"test_uri1\",\"className\":\"ClassC\",\"methodName\":\"MethodZ()\"}]}]",
                HttpStatus.OK
        );
    }
}
