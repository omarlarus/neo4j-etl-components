package org.neo4j.integration.sql.exportcsv.mapping;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.neo4j.integration.sql.metadata.Column;
import org.neo4j.integration.neo4j.importcsv.fields.CsvField;
import org.neo4j.integration.util.Preconditions;

import static java.lang.String.format;

public class ColumnToCsvFieldMappings
{
    public static Builder builder()
    {
        return new ColumnToCsvFieldMappingsBuilder();
    }

    private final Map<Column, CsvField> mappings;

    ColumnToCsvFieldMappings( Map<Column, CsvField> mappings )
    {
        this.mappings = Collections.unmodifiableMap( Preconditions.requireNonEmptyMap( mappings, "Mappings" ) );
    }

    public Collection<CsvField> fields()
    {
        return mappings.values();
    }

    public Collection<Column> columns()
    {
        return mappings.keySet();
    }

    public Collection<String> aliasedColumns(  )
    {
        return columns().stream()
                .map( c -> format( "%s AS %s", c.name(), c.alias() ) )
                .collect( Collectors.toList() );
    }

    public Collection<String> tableNames()
    {
        return mappings.keySet().stream()
                .map( c -> c.table().fullName() )
                .collect( Collectors.toCollection( LinkedHashSet::new ) );
    }

    public interface Builder
    {
        Builder add( Column from, CsvField to );

        ColumnToCsvFieldMappings build();
    }
}
