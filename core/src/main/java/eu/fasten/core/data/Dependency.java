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
import org.json.JSONObject;
import org.json.JSONArray;

public class Dependency {
    final private String product;
    final private String forge;
    final private String[] constraints;

    public Dependency(final JSONObject json) {
        this.product = json.getString("product");
        this.forge = json.getString("forge");
        this.constraints = parseConstraints(json.getJSONArray("constraints"));
    }

    protected String[] parseConstraints(JSONArray constraints) {
        var arr = new String[constraints.length()];
        for (int i = 0; i < constraints.length(); ++i) {
            arr[i] = constraints.getString(i);
        }
        return arr;
    }

    public JSONObject toJSON() {
        final var json = new JSONObject();
        final var arr = new JSONArray();

        json.put("product", product);
        json.put("forge", forge);
        for (var cs : constraints) {
            arr.put(cs);
        }
        json.put("constraints", arr);
        return json;
    }

    public String getProduct() {
        return product;
    }

    public String getForge() {
        return forge;
    }

    public String[] getConstraints() {
        return constraints;
    }
}
