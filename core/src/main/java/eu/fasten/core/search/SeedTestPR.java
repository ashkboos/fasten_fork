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

package eu.fasten.core.search;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.ClosestFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jooq.DSLContext;
import org.jooq.conf.ParseUnknownFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Doubles;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.DependencyEdge;
import eu.fasten.core.maven.data.Revision;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.lang.ObjectParser;

/**
 * Computes statistics for seeding.
 */

public class SeedTestPR {
	private static final Logger LOGGER = LoggerFactory.getLogger(SeedTestPR.class);

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;

	/**
	 * Creates a new seed test using a given JDBC URI, database name and path to RocksDB.
	 *
	 * @implNote This method creates a {@linkplain DSLContext context}, {@linkplain RocksDao RocksDB
	 *           DAO}, and {@linkplain Scorer scorer} using the given parameters and delegates to
	 *           {@link #SearchEngine(DSLContext, RocksDao, String, Scorer)}.
	 *
	 * @param jdbcURI the JDBC URI.
	 * @param database the database name.
	 * @param rocksDb the path to the RocksDB database of revision call graphs.
	 * @param resolverGraph the path to a serialized resolver graph (will be created if it does not
	 *            exist).
	 * @param scorer an {@link ObjectParser} specification providing a scorer; if {@code null}, a
	 *            {@link TrivialScorer} will be used instead.
	 */
	public SeedTestPR(final String jdbcURI, final String database, final String rocksDb, final String resolverGraph, final String scorer) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database, false), null, resolverGraph, scorer == null ? TrivialScorer.getInstance() : ObjectParser.fromSpec(scorer, Scorer.class));
	}

	/**
	 * Creates a new seed test using a given {@link DSLContext} and {@link RocksDao}.
	 *
	 * @param context the DSL context.
	 * @param rocksDao the RocksDB DAO.
	 * @param resolverGraph the path to a serialized resolver graph (will be created if it does not
	 *            exist).
	 * @param scorer a scorer that will be used to sort results; if {@code null}, a
	 *            {@link TrivialScorer} will be used instead.
	 */

	public SeedTestPR(final DSLContext context, final RocksDao rocksDao, final String resolverGraph, final Scorer scorer) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(null, resolverGraph);
		resolver.setIgnoreMissing(true);
	}

	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(SeedTestPR.class.getName(), "Prints statistics on visits on the dependency graph using PageRank to locate important products.", new Parameter[] {
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."),
				new Switch("revisions", 'r', "revisions", "Use directly top revisions."),
				new UnflaggedOption("radius", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Backward ball radius."),
				new UnflaggedOption("n", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Number of seed products."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		@SuppressWarnings("unused")
		final String rocksDb = jsapResult.getString("rocksDB");
		final String resolverGraph = jsapResult.getString("resolverGraph");
		final int radius = jsapResult.getInt("radius");
		final int n = jsapResult.getInt("n");
		final boolean revisions = jsapResult.getBoolean("revisions");

		final SeedTestPR searchEngine = new SeedTestPR(jdbcURI, database, rocksDb, resolverGraph, null);

		final DSLContext context = searchEngine.context;
		context.settings().withParseUnknownFunctions(ParseUnknownFunctions.IGNORE);

		final Graph<Revision, DependencyEdge> graph = GraphMavenResolver.dependencyGraph;

		LOGGER.info("Computing PageRank...");

		final PageRank<Revision, DependencyEdge> pageRank = new PageRank<>(graph, 0.85, 20);
		final Map<Revision, Double> scores = pageRank.getScores();

		LOGGER.info("Sorting revisions by PageRank...");

		final Revision[] revs = graph.vertexSet().toArray(new Revision[0]);
		Arrays.sort(revs, (x, y) -> Doubles.compare(scores.get(y).doubleValue(), scores.get(x).doubleValue()));

		final LinkedHashSet<String> products = new LinkedHashSet<>();
		for(int i = 0; products.size() < n; i++) products.add(revs[i].groupId + ":" + revs[i].artifactId);
		final String[] allSeedProducts = products.toArray(new String[n]);

		LOGGER.info("Seed products: " + Arrays.toString(allSeedProducts));

		for (int r = 1; r <= radius; r++) {
			for (int p = 1; p <= n; p++) {
				final Set<Revision> seeds = new HashSet<>();

				if (revisions) {
					for(int i = 0; i < p; i++) seeds.add(revs[i]);
					LOGGER.info("Using top " + n + " revisions " + seeds);
				}
				else {
					final Set<String> seedProducts = new ObjectLinkedOpenHashSet<>(allSeedProducts, 0, p);
					LOGGER.info("Gatheric revisions of specified products " + seedProducts);
					// Gather all revision with specified product
					for (final Revision rev : graph.vertexSet()) if (seedProducts.contains(rev.groupId + ":" + rev.artifactId)) seeds.add(rev);
					LOGGER.info("Found " + seeds.size() + " revisions for " + p + " products");
				}

				final Set<Revision> backward = new HashSet<>();

				for (final Revision seed : seeds) new ClosestFirstIterator<>(new EdgeReversedGraph<>(graph), seed, r).forEachRemaining(x -> backward.add(x));

				LOGGER.info("Backward visit (radius: " + r + ") expanded " + seeds.size() + " revisions to " + backward.size());

				final int[] c = { 0 };
				new DepthFirstIterator<>(graph, backward).forEachRemaining((unused) -> c[0]++);

				System.out.println(r + "\t" + p + "\t" + c[0]);
			}
		}
	}
}
