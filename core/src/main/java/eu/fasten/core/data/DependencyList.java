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

package eu.fasten.core.data;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONArray;

public class DependencyList {
    public final List<Dependency> dependencies;

    public DependencyList(final JSONArray deps) {
        dependencies = new ArrayList<Dependency>();
        for (int i = 0; i < deps.length(); ++i) {
            var dep = new Dependency(deps.getJSONObject(i));
            dependencies.add(dep);
        }
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public JSONArray toJSON() {
        final var deps = new JSONArray();

        for (final var dep : dependencies) {
            deps.put(dep.toJSON());
        }

        return deps;
    }
}
