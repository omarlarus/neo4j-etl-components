package org.neo4j.mysql;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import org.neo4j.ingest.HeaderFile;
import org.neo4j.mysql.config.ExportProperties;
import org.neo4j.mysql.config.Join;

import static java.util.Arrays.asList;

class ExportJoinCommand
{
    private final ExportProperties properties;
    private final Join join;

    public ExportJoinCommand( ExportProperties properties, Join join )
    {
        this.properties = properties;
        this.join = join;
    }

    public Collection<Path> execute() throws Exception
    {
        String exportId = UUID.randomUUID().toString();

        Path headerFile = new HeaderFile( properties.destination(), properties.formatting() )
                .create( join.fieldMappings(), exportId );
        Path exportFile = new ExportDatabaseContentsCommand( properties ).execute( join, exportId );

        return asList( headerFile, exportFile );
    }
}