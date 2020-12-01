/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables;


import eu.fasten.core.data.metadatadb.codegen.Indexes;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.Public;
import eu.fasten.core.data.metadatadb.codegen.tables.records.DependenciesRecord;

import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.JSONB;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row7;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.12.3"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Dependencies extends TableImpl<DependenciesRecord> {

    private static final long serialVersionUID = -974168096;

    /**
     * The reference instance of <code>public.dependencies</code>
     */
    public static final Dependencies DEPENDENCIES = new Dependencies();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<DependenciesRecord> getRecordType() {
        return DependenciesRecord.class;
    }

    /**
     * The column <code>public.dependencies.package_version_id</code>.
     */
    public final TableField<DependenciesRecord, Long> PACKAGE_VERSION_ID = createField(DSL.name("package_version_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.dependencies.dependency_id</code>.
     */
    public final TableField<DependenciesRecord, Long> DEPENDENCY_ID = createField(DSL.name("dependency_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.dependencies.version_range</code>.
     */
    public final TableField<DependenciesRecord, String[]> VERSION_RANGE = createField(DSL.name("version_range"), org.jooq.impl.SQLDataType.CLOB.getArrayDataType(), this, "");

    /**
     * The column <code>public.dependencies.metadata</code>.
     */
    public final TableField<DependenciesRecord, JSONB> METADATA = createField(DSL.name("metadata"), org.jooq.impl.SQLDataType.JSONB, this, "");

    /**
     * The column <code>public.dependencies.architecture</code>.
     */
    public final TableField<DependenciesRecord, String[]> ARCHITECTURE = createField(DSL.name("architecture"), org.jooq.impl.SQLDataType.CLOB.getArrayDataType(), this, "");

    /**
     * The column <code>public.dependencies.dependency_type</code>.
     */
    public final TableField<DependenciesRecord, String[]> DEPENDENCY_TYPE = createField(DSL.name("dependency_type"), org.jooq.impl.SQLDataType.CLOB.getArrayDataType(), this, "");

    /**
     * The column <code>public.dependencies.alternative_group</code>.
     */
    public final TableField<DependenciesRecord, Long> ALTERNATIVE_GROUP = createField(DSL.name("alternative_group"), org.jooq.impl.SQLDataType.BIGINT, this, "");

    /**
     * Create a <code>public.dependencies</code> table reference
     */
    public Dependencies() {
        this(DSL.name("dependencies"), null);
    }

    /**
     * Create an aliased <code>public.dependencies</code> table reference
     */
    public Dependencies(String alias) {
        this(DSL.name(alias), DEPENDENCIES);
    }

    /**
     * Create an aliased <code>public.dependencies</code> table reference
     */
    public Dependencies(Name alias) {
        this(alias, DEPENDENCIES);
    }

    private Dependencies(Name alias, Table<DependenciesRecord> aliased) {
        this(alias, aliased, null);
    }

    private Dependencies(Name alias, Table<DependenciesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> Dependencies(Table<O> child, ForeignKey<O, DependenciesRecord> key) {
        super(child, key, DEPENDENCIES);
    }

    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.UNIQUE_VERSION_DEPENDENCY_RANGE);
    }

    @Override
    public List<UniqueKey<DependenciesRecord>> getKeys() {
        return Arrays.<UniqueKey<DependenciesRecord>>asList(Keys.UNIQUE_VERSION_DEPENDENCY_RANGE);
    }

    @Override
    public List<ForeignKey<DependenciesRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<DependenciesRecord, ?>>asList(Keys.DEPENDENCIES__DEPENDENCIES_PACKAGE_VERSION_ID_FKEY, Keys.DEPENDENCIES__DEPENDENCIES_DEPENDENCY_ID_FKEY);
    }

    public PackageVersions packageVersions() {
        return new PackageVersions(this, Keys.DEPENDENCIES__DEPENDENCIES_PACKAGE_VERSION_ID_FKEY);
    }

    public Packages packages() {
        return new Packages(this, Keys.DEPENDENCIES__DEPENDENCIES_DEPENDENCY_ID_FKEY);
    }

    @Override
    public Dependencies as(String alias) {
        return new Dependencies(DSL.name(alias), this);
    }

    @Override
    public Dependencies as(Name alias) {
        return new Dependencies(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Dependencies rename(String name) {
        return new Dependencies(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Dependencies rename(Name name) {
        return new Dependencies(name, null);
    }

    // -------------------------------------------------------------------------
    // Row7 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row7<Long, Long, String[], JSONB, String[], String[], Long> fieldsRow() {
        return (Row7) super.fieldsRow();
    }
}
