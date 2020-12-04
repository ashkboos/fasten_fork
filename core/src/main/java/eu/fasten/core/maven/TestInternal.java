package eu.fasten.core.maven;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.List;
import org.jooq.DSLContext;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.FastenJavaURI;

import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.utils.DependencyGraphUtilities;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.DatabaseMerger;

public class TestInternal {

	private static FastenJavaURI getCallableName(final long id, final DSLContext context) {
		return new FastenJavaURI(context.select(Callables.CALLABLES.FASTEN_URI).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(id)).fetchOne().component1());
	}
	public static void main(String[] args) throws IllegalArgumentException, SQLException, Exception {
		var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://monster:5432/fasten_java", "fastenro");
		var rocksDao = new eu.fasten.core.data.graphdb.RocksDao("/home/msokolov/graphdb/", true);

		final var packageName = args[0] + Constants.mvnCoordinateSeparator + args[1];
		final var result = dbContext.select(PackageVersions.PACKAGE_VERSIONS.ID).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName)).and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(args[2])).fetchOne();
		if (result == null) throw new IllegalArgumentException("Revision not in the metadata database");
		final long graphId = result.component1();
		System.out.println("Requested revision has id " + graphId);
		var graph = rocksDao.getGraphData(graphId);
		if (graph == null) throw new IllegalArgumentException("Revision not in the graph database");
		System.out.println("Associated graph has " + graph.numNodes() + " nodes ("  + (graph.numNodes() - graph.externalNodes().size()) + " marked internal) and " + graph.numArcs() + " arcs");
		long internalNodes = 0;
		ObjectOpenHashSet<long[]> internalArcs = new ObjectOpenHashSet<long[]>();
		for(long id: graph.nodes()) {
			FastenJavaURI uri = getCallableName(id, dbContext);
			String path = uri.getPath();
			if (! path.contains("it.unimi.dsi.fastutil") && path.contains("it.unimi.dsi")) {
				internalNodes++;
				for(long s: graph.successors(id)) {
					FastenJavaURI uri2 = getCallableName(s, dbContext);
					String path2 = uri2.getPath();
					if (! path2.contains("it.unimi.dsi.fastutil") && path2.contains("it.unimi.dsi")) {
						internalArcs.add(new long[] {id, s});
						//System.err.println(uri + " -> " + uri2);
					}
							
				}
			}
		}
		System.out.println("Found " + internalNodes + " internal nodes and " + internalArcs.size() + " internal arcs");

		Set<Revision> dependencySet = null;
		GraphMavenResolver graphResolver = new GraphMavenResolver();
		graphResolver.buildDependencyGraph(dbContext, "unused");
		dependencySet = graphResolver.resolveDependencies(args[0], args[1], args[2], -1, dbContext, true);
		
		System.out.println(dependencySet);

		java.util.List<String> depStrings = new java.util.ArrayList<String>();
		LongOpenHashSet deps = new LongOpenHashSet();
		deps.add(graphId);
		for(Revision d: dependencySet) deps.add(d.id);

		DatabaseMerger dm = new DatabaseMerger(deps, dbContext, rocksDao);
		var g = dm.mergeWithCHA(args[0] + ":" + args[1] + ":" + args[2]);
		System.out.println("Stitched graph has " + g.numNodes() + " nodes and " + g.numArcs() + " arcs");
		for(long[] a: internalArcs) 
			if (!g.nodes().contains(a[0]) || !g.nodes().contains(a[1]) || ! g.successors(a[0]).contains(a[1]))
					System.out.println("Internal call " + getCallableName(a[0], dbContext) + " -> " + getCallableName(a[1], dbContext) + " is missing from the stitched graph");
	}
}
