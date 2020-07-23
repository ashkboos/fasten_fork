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

package eu.fasten.analyzer.javacgopalv3;

import eu.fasten.analyzer.javacgopalv3.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopalv3.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopalv3.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopalv3.evaluation.JCGFormat;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.merge.CallGraphMerger;
import eu.fasten.core.merge.CallGraphUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Makes javacg-opal module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGOpal")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.ArgGroup(multiplicity = "1")
    Commands commands;

    @CommandLine.Option(names = {"-o", "--output"},
            paramLabel = "OUT",
            description = "Output directory path",
            defaultValue = "")
    String output;

    static class Commands {
        @CommandLine.ArgGroup(exclusive = false)
        Computations computations;

        @CommandLine.ArgGroup(exclusive = false)
        Conversions conversions;
    }

    static class Computations {
        @CommandLine.Option(names = {"-a", "--artifact"},
                paramLabel = "ARTIFACT",
                description = "Artifact, maven coordinate or file path")
        String artifact;

        @CommandLine.Option(names = {"-m", "--mode"},
                paramLabel = "MODE",
                description = "Input of algorithms are {FILE or COORD}",
                defaultValue = "FILE")
        String mode;

        @CommandLine.Option(names = {"-n", "--main"},
                paramLabel = "MAIN",
                description = "Main class of artifact")
        String main;

        @CommandLine.Option(names = {"-ga", "--genAlgorithm"},
                paramLabel = "GenALG",
                description = "gen{RTA,CHA,AllocationSiteBasedPointsTo,TypeBasedPointsTo}",
                defaultValue = "CHA")
        String genAlgorithm;

        @CommandLine.ArgGroup()
        Tools tools;

        @CommandLine.Option(names = {"-t", "--timestamp"},
                paramLabel = "TS",
                description = "Release TS",
                defaultValue = "0")
        String timestamp;
    }

    static class Tools {
        @CommandLine.ArgGroup(exclusive = false)
        Opal opal;

        @CommandLine.ArgGroup(exclusive = false)
        Merge merge;
    }

    static class Opal {
        @CommandLine.Option(names = {"-g", "--generate"},
                paramLabel = "GEN",
                description = "Generate call graph for artifact")
        boolean doGenerate;
    }

    static class Merge {
        @CommandLine.Option(names = {"-ma", "--mergeAlgorithm"},
                paramLabel = "MerALG",
                description = "Algorhtm merge{RA, CHA}",
                defaultValue = "CHA")
        String mergeAlgorithm;

        @CommandLine.Option(names = {"-s", "--stitch"},
                paramLabel = "STITCH",
                description = "Stitch artifact CG to dependencies")
        boolean doMerge;

        @CommandLine.Option(names = {"-d", "--dependencies"},
                paramLabel = "DEPS",
                description = "Dependencies, coordinates or files",
                split = ",")
        List<String> dependencies;
    }

    static class Conversions {
        @CommandLine.Option(names = {"-c", "--convert"},
                paramLabel = "CON",
                description = "Convert the call graph to the specified format")
        boolean doConvert;

        @CommandLine.Option(names = {"-i", "--input"},
                paramLabel = "IN",
                description = "Path to the input call graph for conversion",
                required = true,
                split = ",")
        List<String> input;

        @CommandLine.Option(names = {"-f", "--format"},
                paramLabel = "FORMAT",
                description = "The desired format for conversion {JCG}",
                defaultValue = "JCG")
        String format;
    }

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line
     * parameters.
     */
    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }

    /**
     * Run the generator, merge algorithm or evaluator depending on parameters provided.
     */
    public void run() {
        if (this.commands.computations != null && this.commands.computations.tools != null) {
            if (this.commands.computations.tools.opal != null
                    && this.commands.computations.tools.opal.doGenerate) {
                runGenerate();
            }
            if (this.commands.computations.tools.merge != null
                    && this.commands.computations.tools.merge.doMerge) {
                runMerge();
            }
        }
        if (this.commands.conversions != null && this.commands.conversions.doConvert) {
            runConversion();
        }

    }

    /**
     * Run call graph generator.
     */
    private void runGenerate() {
        if (commands.computations.mode.equals("COORD")) {
            final var artifact = getArtifactCoordinate();
            logger.info("Generating call graph for the Maven coordinate: {}",
                    artifact.getCoordinate());
            try {
                generate(artifact, commands.computations.main, commands.computations.genAlgorithm,
                        !this.output.isEmpty());
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (commands.computations.mode.equals("FILE")) {
            try {
                generate(getArtifactFile(), commands.computations.main,
                        commands.computations.genAlgorithm, !this.output.isEmpty());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Run merge algorithm.
     */
    private void runMerge() {
        if (commands.computations.mode.equals("COORD")) {
            try {
                merge(getArtifactCoordinate(), getDependenciesCoordinates());
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (commands.computations.mode.equals("FILE")) {
            try {
                merge(getArtifactFile(), getDependenciesFiles()).toJSON();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Run evaluator.
     */
    private void runConversion() {
        if (this.commands.conversions.format.equals("JCG")) {
            final var result = new JSONObject();
            var reachableMethods = new JSONArray();
            try {
                for (final var input : this.commands.conversions.input) {
                    final var cg = new String(Files.readAllBytes(Paths.get(input)));

                    final var mergeJCG = JCGFormat
                            .convertERCGTOJCG(new ExtendedRevisionCallGraph(new JSONObject(cg)));
                    if (!mergeJCG.isEmpty() && !mergeJCG.isNull("reachableMethods")) {
                        reachableMethods = concatArray(reachableMethods,
                                mergeJCG.getJSONArray("reachableMethods"));
                    }
                }
                result.put("reachableMethods", reachableMethods);
                if (!this.output.isEmpty()) {
                    CallGraphUtils.writeToFile(this.output, result, "");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Concatenate two JSON arrays.
     *
     * @param arr1 first array
     * @param arr2 second array
     * @return concatenated JSON array
     * @throws JSONException thrown if JSON arrays are malformed
     */
    private JSONArray concatArray(JSONArray arr1, JSONArray arr2)
            throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < arr1.length(); i++) {
            result.put(arr1.get(i));
        }
        for (int i = 0; i < arr2.length(); i++) {
            result.put(arr2.get(i));
        }
        return result;
    }

    /**
     * Merge an artifact with a list of it's dependencies using a specified algorithm.
     *
     * @param artifact     artifact to merge
     * @param dependencies list of dependencies
     * @param <T>          artifact can be either a file or coordinate
     * @return a revision call graph with resolved class hierarchy and calls
     * @throws IOException thrown in case file related exceptions occur, e.g FileNotFoundException
     */
    public <T> ExtendedRevisionCallGraph merge(final T artifact,
                                               final List<T> dependencies) throws IOException {

        final ExtendedRevisionCallGraph result;
        final var deps = new ArrayList<ExtendedRevisionCallGraph>();
        for (final var dep : dependencies) {
            deps.add(generate(dep, "", commands.computations.genAlgorithm, true));
        }
        final var art = generate(artifact, this.commands.computations.main,
                commands.computations.genAlgorithm, true);

        result = CallGraphMerger.mergeCallGraph(art, deps,
                commands.computations.tools.merge.mergeAlgorithm);

        if (!this.output.isEmpty() && result != null) {
            CallGraphUtils.writeToFile(this.output, result.toJSON(),
                    "_" + result.product + "_merged");
        }

        return result;
    }

    /**
     * Generate a revision call graph for a given coordinate using a specified algorithm. In case
     * the artifact is an application a main class can be specified. If left empty a library entry
     * point finder algorithm will be used.
     *
     * @param artifact    artifact to generate a call graph for
     * @param mainClass   main class in case the artifact is an application
     * @param algorithm   algorithm for generating a call graph
     * @param writeToFile will be written to a file if true
     * @param <T>         artifact can be either a file or a coordinate
     * @return generated revision call graph
     * @throws IOException file related exceptions, e.g. FileNotFoundException
     */
    public <T> ExtendedRevisionCallGraph generate(final T artifact,
                                                  final String mainClass,
                                                  final String algorithm, final boolean writeToFile)
            throws IOException {
        final ExtendedRevisionCallGraph revisionCallGraph;

        final long startTime = System.currentTimeMillis();

        if (artifact instanceof File) {
            logger.info("Generating graph for {}", ((File) artifact).getAbsolutePath());
            final var cg = new PartialCallGraph(
                    new CallGraphConstructor((File) artifact, mainClass, algorithm));
            revisionCallGraph =
                    ExtendedRevisionCallGraph.extendedBuilder().graph(cg.getGraph())
                            .product(cleanUpFileName((File) artifact))
                            .version("").timestamp(0).cgGenerator("").forge("")
                            .classHierarchy(cg.getClassHierarchy()).nodeCount(cg.getNodeCount()).build();

        } else {
            revisionCallGraph = PartialCallGraph
                    .createExtendedRevisionCallGraph((MavenCoordinate) artifact, mainClass,
                            algorithm, Long.parseLong(this.commands.computations.timestamp));
        }

        logger.info("Generated the call graph in {} seconds.", new DecimalFormat("#0.000")
                .format((System.currentTimeMillis() - startTime) / 1000d));

        if (writeToFile) {
            CallGraphUtils.writeToFile(this.output, revisionCallGraph.toJSON(), "");
        }
        return revisionCallGraph;
    }

    private String cleanUpFileName(File artifact) {
        return artifact.getName().replace(".class", "").replace("$", "").replace(".jar", "");
    }

    /**
     * Get a list of files of dependencies.
     *
     * @return a list of dependencies files
     */
    private List<File> getDependenciesFiles() {
        final var result = new ArrayList<File>();
        if (this.commands.computations.tools.merge.dependencies != null) {
            for (String currentCoordinate : this.commands.computations.tools.merge.dependencies) {
                result.add(new File(currentCoordinate));
            }
        }
        return result;
    }

    /**
     * Get a list of coordinates of dependencies.
     *
     * @return a list of Maven coordinates
     */
    private List<MavenCoordinate> getDependenciesCoordinates() {
        final var result = new ArrayList<MavenCoordinate>();
        if (this.commands.computations.tools.merge.dependencies != null) {
            for (String currentCoordinate : this.commands.computations.tools.merge.dependencies) {
                result.add(MavenCoordinate.fromString(currentCoordinate));
            }
        }
        return result;
    }

    /**
     * Get an artifact file from a provided path.
     *
     * @return artifact file
     */
    private File getArtifactFile() {
        File result = null;
        if (this.commands.computations.artifact != null) {
            result = new File(this.commands.computations.artifact);
        }
        return result;
    }

    /**
     * Get an artifact coordinate for an artifact.
     *
     * @return artifact coordinate
     */
    private MavenCoordinate getArtifactCoordinate() {
        MavenCoordinate result = null;
        if (this.commands.computations.artifact != null) {
            result = MavenCoordinate.fromString(this.commands.computations.artifact);
        }
        return result;
    }

    /**
     * Set out of the OPAL plugin.
     *
     * @param output new output path
     */
    public void setOutput(String output) {
        this.output = output;
    }
}
