/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.routines;


import eu.fasten.core.data.metadatadb.codegen.Public;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.Parameter;
import org.jooq.impl.AbstractRoutine;
import org.jooq.impl.Internal;


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
public class PgpSymEncryptBytea2 extends AbstractRoutine<byte[]> {

    private static final long serialVersionUID = -108123741;

    /**
     * The parameter <code>public.pgp_sym_encrypt_bytea.RETURN_VALUE</code>.
     */
    public static final Parameter<byte[]> RETURN_VALUE = Internal.createParameter("RETURN_VALUE", org.jooq.impl.SQLDataType.BLOB, false, false);

    /**
     * The parameter <code>public.pgp_sym_encrypt_bytea._1</code>.
     */
    public static final Parameter<byte[]> _1 = Internal.createParameter("_1", org.jooq.impl.SQLDataType.BLOB, false, true);

    /**
     * The parameter <code>public.pgp_sym_encrypt_bytea._2</code>.
     */
    public static final Parameter<String> _2 = Internal.createParameter("_2", org.jooq.impl.SQLDataType.CLOB, false, true);

    /**
     * The parameter <code>public.pgp_sym_encrypt_bytea._3</code>.
     */
    public static final Parameter<String> _3 = Internal.createParameter("_3", org.jooq.impl.SQLDataType.CLOB, false, true);

    /**
     * Create a new routine call instance
     */
    public PgpSymEncryptBytea2() {
        super("pgp_sym_encrypt_bytea", Public.PUBLIC, org.jooq.impl.SQLDataType.BLOB);

        setReturnParameter(RETURN_VALUE);
        addInParameter(_1);
        addInParameter(_2);
        addInParameter(_3);
        setOverloaded(true);
    }

    /**
     * Set the <code>_1</code> parameter IN value to the routine
     */
    public void set__1(byte... value) {
        setValue(_1, value);
    }

    /**
     * Set the <code>_1</code> parameter to the function to be used with a {@link org.jooq.Select} statement
     */
    public void set__1(Field<byte[]> field) {
        setField(_1, field);
    }

    /**
     * Set the <code>_2</code> parameter IN value to the routine
     */
    public void set__2(String value) {
        setValue(_2, value);
    }

    /**
     * Set the <code>_2</code> parameter to the function to be used with a {@link org.jooq.Select} statement
     */
    public void set__2(Field<String> field) {
        setField(_2, field);
    }

    /**
     * Set the <code>_3</code> parameter IN value to the routine
     */
    public void set__3(String value) {
        setValue(_3, value);
    }

    /**
     * Set the <code>_3</code> parameter to the function to be used with a {@link org.jooq.Select} statement
     */
    public void set__3(Field<String> field) {
        setField(_3, field);
    }
}