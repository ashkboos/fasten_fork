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

package eu.fasten.core.data.graphdb;

import eu.fasten.core.data.metadatadb.codegen.enums.CallType;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.math3.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExtendedGidGraph extends GidGraph {

    private final Map<Pair<Long, Long>, EdgesRecord> callInfo = new HashMap<>();
    private final Map<Long, String> gidToUriMap;
    private final Map<Long, String> typeMap;

    /**
     * Constructor for Graph.
     *
     * @param index            ID of the graph (index from postgres)
     * @param product          Product name
     * @param version          Product version
     * @param nodes            List of Global IDs of nodes of the graph
     *                         (first internal nodes, then external nodes)
     * @param numInternalNodes Number of internal nodes in nodes list
     * @param edges            List of edges of the graph with pairs for Global IDs
     */
    public ExtendedGidGraph(long index, String product, String version, List<Long> nodes, int numInternalNodes, List<EdgesRecord> edges, Map<Long, String> gid2UriMap, Map<Long, String> typeMap) {
        super(index, product, version, nodes, numInternalNodes, edges);
        this.gidToUriMap = gid2UriMap;
        edges.forEach(e -> callInfo.put(new Pair<>(e.getSourceId(), e.getTargetId()), e));
        this.typeMap = typeMap;
    }

    public Map<Pair<Long, Long>, EdgesRecord> getCallsInfo() {
        return this.callInfo;
    }

    public List<List<Long>> getEdges() {
        return this.callInfo.keySet().stream().map(p -> List.of(p.getFirst(), p.getSecond())).collect(Collectors.toList());
    }

    public Map<Long, String> getGidToUriMap() {
        return gidToUriMap;
    }

    public Map<Long, String> getTypeMap() {
        return typeMap;
    }

    @Override
    public JSONObject toJSON() {
        throw new NotImplementedException();
    }

    @Override
    public boolean equals(Object o) {
        throw new NotImplementedException();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (callInfo != null ? callInfo.hashCode() : 0);
        result = 31 * result + (gidToUriMap != null ? gidToUriMap.hashCode() : 0);
        return result;
    }

    public static ExtendedGidGraph getGraph(JSONObject jsonGraph) throws JSONException {
        throw new NotImplementedException();
    }

    private static CallType getCallType(String type) {
        switch (type.toLowerCase()) {
            case "static":
                return CallType.static_;
            case "dynamic":
                return CallType.dynamic;
            case "virtual":
                return CallType.virtual;
            case "interface":
                return CallType.interface_;
            case "special":
                return CallType.special;
            default:
                throw new IllegalArgumentException("Unknown call type: " + type);
        }
    }
}