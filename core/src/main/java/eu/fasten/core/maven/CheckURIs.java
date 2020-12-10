package eu.fasten.core.maven;

import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.jooq.DSLContext;
import org.json.JSONObject;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.DatabaseMerger;
import eu.fasten.core.merge.LocalMerger;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class CheckURIs {

	private static FastenJavaURI getCallableName(final long id, final DSLContext context) {
		return new FastenJavaURI(context.select(Callables.CALLABLES.FASTEN_URI).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(id)).fetchOne().component1());
	}

	public static void main(final String[] args) throws IllegalArgumentException, SQLException, Exception {
		final var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://monster:5432/fasten_java", "fastenro");
		final var groupId = args[0];
		final var artifactId = args[1];
		final var version = args[2];

		final var packageName = groupId + Constants.mvnCoordinateSeparator + artifactId;
		final var result = dbContext.select(PackageVersions.PACKAGE_VERSIONS.ID).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName)).and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version)).fetchOne();
		if (result == null) throw new IllegalArgumentException("Revision not in the metadata database");
		final long graphId = result.component1();
		System.out.println("Requested revision has id " + graphId);

		final var erjcg = new ExtendedRevisionJavaCallGraph(new JSONObject(IOUtils.toString(new FileInputStream("/mnt/fasten/mvn/" + artifactId.charAt(0) + "/" + artifactId + "/" + artifactId + "_" + groupId + "_" + version + ".json"))));

		Set<Revision> dependencySet = java.util.Collections.emptySet();
		final GraphMavenResolver graphResolver = new GraphMavenResolver();
		graphResolver.buildDependencyGraph(dbContext, "unused");
		dependencySet = graphResolver.resolveDependencies(groupId, artifactId, version, -1, dbContext, true);

		System.out.println(dependencySet);

		final List<ExtendedRevisionJavaCallGraph> deps = new ArrayList<>();

		for (final Revision d : dependencySet) {
			final String filename = "/mnt/fasten/mvn/" + d.artifactId.charAt(0) + "/" + d.artifactId + "/" + d.artifactId + "_" + d.groupId + "_" + d.version + ".json";
			if (new java.io.File(filename).exists()) {
				System.out.println("Adding " + d);
				deps.add(new ExtendedRevisionJavaCallGraph(new JSONObject(IOUtils.toString(new FileReader(filename, StandardCharsets.UTF_8)))));
			}
		}

		// Local merging
		final var lm = new LocalMerger(deps);
		final var mergedERJCG = lm.mergeWithCHA(erjcg);
		final var localGraph = mergedERJCG.getGraph();

		// Local node set
		HashSet<FastenURI> s = new HashSet<>();
		long dups = 0;
		for(var u: mergedERJCG.mapOfAllMethods().values()) 
			if (! s.add(u.getUri())) {
				dups++;
				System.out.println("Duplicate URI " + u.getUri());
			}

		System.out.println("Duplicate URIs: " + dups + " (" + 100.0 * dups / mergedERJCG.mapOfAllMethods().size() + "%)");
	}
}
