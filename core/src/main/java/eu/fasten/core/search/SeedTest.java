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

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongPredicate;

import org.jgrapht.Graph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.ClosestFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jooq.DSLContext;
import org.jooq.conf.ParseUnknownFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.DependencyEdge;
import eu.fasten.core.maven.data.Revision;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.lang.ObjectParser;

/**
 * Computes statistics for seeding.
 */

public class SeedTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(SeedTest.class);

	private static final int DEFAULT_LIMIT = 10;

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
	public SeedTest(final String jdbcURI, final String database, final String rocksDb, final String resolverGraph, final String scorer) throws Exception {
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

	public SeedTest(final DSLContext context, final RocksDao rocksDao, final String resolverGraph, final Scorer scorer) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(null, resolverGraph);
		resolver.setIgnoreMissing(true);
	}

	/**
	 * Performs a breadth-first visit of the given graph, starting from the provided seed, using the
	 * provided predicate and returning a collection of {@link Result} instances scored using the
	 * provided scorer and satisfying the provided filter.
	 *
	 * @param graph a {@link DirectedGraph}.
	 * @param forward if true, the visit follows arcs; if false, the visit follows arcs backwards.
	 * @param seed an initial seed; may contain GIDs that do not appear in the graph, which will be
	 *            ignored.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @param scorer a scorer that will be used to score the results.
	 * @param results a list of {@linkplain Result results} that will be filled during the visit;
	 *            pre-existing results will not be modified.
	 */
	protected static void bfs(final DirectedGraph graph, final boolean forward, final LongCollection seed, final LongPredicate filter, final Scorer scorer) {
		final LongArrayFIFOQueue queue = new LongArrayFIFOQueue(seed.size());
		seed.forEach(x -> queue.enqueue(x)); // Load initial state
		final LongOpenHashSet seen = new LongOpenHashSet();
		seed.forEach(x -> seen.add(x)); // Load initial state
		int d = -1;
		long sentinel = queue.firstLong();
		// final Result probe = new Result();
		final LongSet nodes = graph.nodes();

		while (!queue.isEmpty()) {
			final long gid = queue.dequeueLong();
			if (gid == sentinel) {
				d++;
				sentinel = -1;
			}

			if (!nodes.contains(gid)) continue; // We accept arbitrary seed sets

			if (!seed.contains(gid) && filter.test(gid)) {
				// TODO: maybe we want to update in case of improved score?
				// probe.gid = gid;
				// if (!results.contains(probe)) results.add(new Result(gid, scorer.score(graph, gid, d)));
			}

			final LongIterator iterator = forward ? graph.successors(gid).iterator() : graph.predecessors(gid).iterator();

			while (iterator.hasNext()) {
				final long x = iterator.nextLong();
				if (seen.add(x)) {
					if (sentinel == -1) sentinel = x;
					queue.enqueue(x);
				}
			}
		}
	}

	@SuppressWarnings("boxing")
	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(SeedTest.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."),
				new UnflaggedOption("radius", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Backward ball radius."),
				new UnflaggedOption("seedProducts", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.GREEDY, "Seed products."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		@SuppressWarnings("unused")
		final String rocksDb = jsapResult.getString("rocksDB");
		final String resolverGraph = jsapResult.getString("resolverGraph");
		final int radius = jsapResult.getInt("radius");
		final String[] allSeedProducts = jsapResult.getStringArray("seedProducts");
		/* WARNING
		 *
		 * As of JDK 11.0.10, replacing the constant string below with the parameter "rocksDb" causes
		 * a JVM crash with the following stack trace:
		 *
		 * V  [libjvm.so+0x5ad861]  AccessInternal::PostRuntimeDispatch<G1BarrierSet::AccessBarrier<1097844ul, G1BarrierSet>, (AccessInternal::BarrierType)2, 1097844ul>::oop_access_barrier(void*)+0x1
		 * C  [librocksdbjni5446245757426305293.so+0x22aefc]  rocksdb_open_helper(JNIEnv_*, long, _jstring*, _jobjectArray*, _jlongArray*, std::function<rocksdb::Status (rocksdb::DBOptions const&, std::string const&, std::vector<rocksdb::ColumnFamilyDescriptor, std::allocator<rocksdb::ColumnFamilyDescriptor> > const&, std::vector<rocksdb::ColumnFamilyHandle*, std::allocator<rocksdb::ColumnFamilyHandle*> >*, rocksdb::DB**)>)+0x3c
		 * C  [librocksdbjni5446245757426305293.so+0x22b371]  Java_org_rocksdb_RocksDB_openROnly__JLjava_lang_String_2_3_3B_3JZ+0x41
		 * j  org.rocksdb.RocksDB.openROnly(JLjava/lang/String;[[B[JZ)[J+0
		 *
		 * The most likely explanation is some kind of aggressive early collection of the variable rocksDb by the G1
		 * collector which clashes with RocksDB's JNI usage of the variable.
		 */

		//final SearchEngine searchEngine = new SearchEngine(jdbcURI, database, "/mnt/fasten/graphdb.old", resolverGraph, null);
		final SeedTest searchEngine = new SeedTest(jdbcURI, database, rocksDb, resolverGraph, null);

		final DSLContext context = searchEngine.context;
		context.settings().withParseUnknownFunctions(ParseUnknownFunctions.IGNORE);

		final Graph<Revision, DependencyEdge> graph = GraphMavenResolver.dependencyGraph;

		for (int r = 1; r <= radius; r++) {
			for (int p = 1; p < allSeedProducts.length; p++) {
				final Set<String> seedProducts = new ObjectOpenHashSet<>(allSeedProducts, 0, p);
				final Set<Revision> seeds = new HashSet<>();

				LOGGER.info("Gatheric revisions of specified products " + seedProducts);

				// Gather all revision with specified product
				for (final Revision rev : graph.vertexSet())
					if (seedProducts.contains(rev.groupId + ":" + rev.artifactId)) seeds.add(rev);

				LOGGER.info("Found " + seeds.size() + " revisions for " + seedProducts.size() + " products");

				final Set<Revision> backward = new HashSet<>();

				for (final Revision seed : seeds)
					new ClosestFirstIterator<>(new EdgeReversedGraph<>(graph), seed, r).forEachRemaining(x -> backward.add(x));

				LOGGER.info("Backward visit (radius: " + radius + ") expanded " + seeds.size() + " revisions to " + backward.size());

				final int[] c = { 0 };
				new DepthFirstIterator<>(graph, backward).forEachRemaining((unused) -> c[0]++);

				System.out.println(r + "\t" + p + "\t" + c[0]);
			}
		}
	}
}