package com.dispatchflow.shared.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Every stored list refuses a negative position (ADR-128 decision 4). Hibernate throws {@code
 * IndexOutOfBoundsException} while it loads a list with one, before the root's load check can
 * refuse the damage, so the schema has to.
 */
@IntegrationTest
class ListPositionsIT {

    @Autowired
    private JdbcClient jdbc;

    /**
     * A list's order column is named {@code position} in each of the eight module schemas (V1).
     * PostgreSQL prints the column quoted, because {@code position} is a keyword.
     */
    @Test
    void GivenTheModulesSchemas_WhenTheirListTablesAreRead_ThenEachRefusesANegativePosition() {
        List<String> lists = jdbc.sql("""
                        SELECT table_schema || '.' || table_name FROM information_schema.columns
                        WHERE column_name = 'position' AND table_schema IN (
                            'access', 'audit', 'fleet', 'liveops', 'messaging', 'orders', 'planning', 'reporting')
                        """).query(String.class).list();
        List<String> refusingANegativePosition =
                jdbc.sql("""
                        SELECT conrelid::regclass::text FROM pg_constraint
                        WHERE contype = 'c' AND replace(pg_get_constraintdef(oid), '"', '') = 'CHECK ((position >= 0))'
                        """).query(String.class).list();

        assertThat(lists).isNotEmpty();
        assertThat(refusingANegativePosition).containsExactlyInAnyOrderElementsOf(lists);
    }
}
