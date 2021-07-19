package eu.fasten.core.maven;

import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Revision;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "DependentResolver")
public class DependentResolver implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(GraphMavenResolver.class);

    @CommandLine.Option(names = {"-p", "--serializedPath"},
        paramLabel = "PATH",
        description = "Path to load a serialized Maven dependency graph from",
        required = true)
    protected String serializedPath;

    @CommandLine.Option(names = {"-d", "--database"},
        paramLabel = "DB_URL",
        description = "Database URL for connection",
        defaultValue = "jdbc:postgresql:postgres")
    protected String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
        paramLabel = "DB_USER",
        description = "Database user name",
        defaultValue = "postgres")
    protected String dbUser;

    @CommandLine.Option(names = {"-i", "--input"},
        paramLabel = "IN_PATH",
        description = "path to the input file",
        defaultValue = "in.txt")
    protected String inPath;

    @CommandLine.Option(names = {"-o", "--output"},
        paramLabel = "OUT_PATH",
        description = "path to output file",
        defaultValue = "out.txt")
    protected String outPath;

    @CommandLine.Option(names = {"-t", "--transitive"},
        paramLabel = "INCL_TRANSITIVES",
        description = "whether or not include transitive dependents")
    protected boolean inclTransitive;

    @CommandLine.Option(names = {"-r", "--repositories"},
        paramLabel = "FIND_REPOSITORIES",
        description = "whether or not find repositories of input file coordinates")
    protected boolean findRepos;

    @Override
    public void run() {
        DSLContext dbContext = null;

        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        assert dbContext != null;
        if (findRepos) {
            selectRepositories(inPath, outPath, dbContext);
        } else {
            selectAllDependents(inPath, outPath, dbContext, inclTransitive, serializedPath);
        }
    }

    private void selectRepositories(final String inPath, final String outPath,
                                    final DSLContext dbContext) {
        Map<String, Set<String>> result = new HashMap<>();
        final var file = new File(inPath);
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                var coord = line.split(":");
                var deps =
                    dbContext.select(Packages.PACKAGES.PACKAGE_NAME,
                        PackageVersions.PACKAGE_VERSIONS.VERSION)
                        .from(PackageVersions.PACKAGE_VERSIONS)
                        .join(Packages.PACKAGES)
                        .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                        .where(Packages.PACKAGES.PACKAGE_NAME.eq(coord[0] + ":" + coord[1]))
                        .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(coord[2]))
                        .fetch();
                result.put(coord[0] + ":" + coord[1] + ":" + coord[2],
                    deps.intoSet(stringStringRecord2 -> stringStringRecord2.component1() + ":" +
                        stringStringRecord2.component2()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        writeToFile(outPath, result);
    }

    private void writeToFile(String outPath, Map<String, Set<String>> result) {
        File output = new File(outPath);

        BufferedWriter bf = null;
        try {
            bf = new BufferedWriter(new FileWriter(output));

            for (final var entry : result.entrySet()) {
                bf.write(entry.getKey() + ";" + entry.getValue().toString());
                bf.newLine();
            }
            bf.flush();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                assert bf != null;
                bf.close();
            }
            catch (Exception ignored) {
            }
        }
    }

    private void selectAllDependents(final String vulCoordsPath,
                                            final String outputPath, final DSLContext dbContext,
                                            final boolean transitive, final String serializedPath) {
        final var file = new File(vulCoordsPath);
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            var resolver = new GraphMavenResolver();
            resolver.buildDependencyGraph(dbContext, serializedPath);
            Map<String, Set<String>> result = new HashMap<>();
            while ((line = br.readLine()) != null) {
                var coord = line.split(":");
                final var dependents = resolver.resolveDependents(coord[0], coord[1], coord[2],-1,
                    transitive);
                result.put(line,
                    dependents.stream().map(Revision::toString).collect(Collectors.toSet()));
            }
            writeToFile(outputPath, result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
