package org.neo4j.integration.cli.mysql;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.airline.Command;
import io.airlift.airline.Option;
import io.airlift.airline.OptionType;
import org.apache.commons.lang3.StringUtils;

import org.neo4j.integration.commands.mysql.CreateCsvResources;
import org.neo4j.integration.commands.mysql.ExportFromMySql;
import org.neo4j.integration.environment.CsvDirectorySupplier;
import org.neo4j.integration.environment.DestinationDirectorySupplier;
import org.neo4j.integration.environment.Environment;
import org.neo4j.integration.environment.EnvironmentSupplier;
import org.neo4j.integration.environment.ImportToolDirectorySupplier;
import org.neo4j.integration.neo4j.importcsv.config.Delimiter;
import org.neo4j.integration.neo4j.importcsv.config.Formatting;
import org.neo4j.integration.neo4j.importcsv.config.QuoteChar;
import org.neo4j.integration.sql.ConnectionConfig;
import org.neo4j.integration.sql.DatabaseType;
import org.neo4j.integration.sql.exportcsv.mapping.CsvResources;
import org.neo4j.integration.sql.exportcsv.mysql.MySqlExportSqlSupplier;
import org.neo4j.integration.util.CliRunner;
import org.neo4j.integration.util.Loggers;

import static java.lang.String.format;

@Command(name = "export", description = "Export from MySQL.")
public class ExportFromMySqlCli implements Runnable
{
    public static final Delimiter DEFAULT_DELIMITER = new Delimiter( "," );
    public static final QuoteChar DEFAULT_QUOTE_CHAR = QuoteChar.DOUBLE_QUOTES;
    @SuppressWarnings("FieldCanBeLocal")
    @Option(type = OptionType.COMMAND,
            name = {"-h", "--host"},
            description = "Host to use for connection to MySQL.",
            title = "name",
            required = false)
    private String host = "localhost";

    @SuppressWarnings("FieldCanBeLocal")
    @Option(type = OptionType.COMMAND,
            name = {"-p", "--port"},
            description = "Port number to use for connection to MySQL.",
            title = "#",
            required = false)
    private int port = 3306;

    @Option(type = OptionType.COMMAND,
            name = {"-u", "--user"},
            description = "User for login to MySQL.",
            title = "name",
            required = true)
    private String user;

    @Option(type = OptionType.COMMAND,
            name = {"--password"},
            description = "Password for login to MySQL.",
            title = "name",
            required = true)
    private String password;

    @Option(type = OptionType.COMMAND,
            name = {"-d", "--database"},
            description = "MySQL database.",
            title = "name",
            required = true)
    private String database;

    @Option(type = OptionType.COMMAND,
            name = {"--import-tool"},
            description = "Path to directory containing Neo4j import tool.",
            title = "directory",
            required = true)
    private String importToolDirectory;

    @Option(type = OptionType.COMMAND,
            name = {"--import-tool-options"},
            description = "Path to directory containing options to Neo4j import tool.",
            title = "directory")
    private String importToolOptionsDirectory;

    @Option(type = OptionType.COMMAND,
            name = {"--csv-directory"},
            description = "Path to directory for intermediate CSV files.",
            title = "directory",
            required = true)
    private String csvRootDirectory;

    @Option(type = OptionType.COMMAND,
            name = {"--destination"},
            description = "Path to destination store directory (this will overwrite any exiting directory).",
            title = "directory",
            required = true)
    private String destinationDirectory;

    @SuppressWarnings("FieldCanBeLocal")
    @Option(type = OptionType.COMMAND,
            name = {"--force"},
            description = "Force delete destination store directory if it already exists.")
    private boolean force = false;

    @Option(type = OptionType.COMMAND,
            name = {"--csv-resources-uri"},
            description = "URI of an existing CSV resources definitions file.",
            title = "uri",
            required = false)
    private String csvResourcesUri;

    @SuppressWarnings("FieldCanBeLocal")
    @Option(type = OptionType.COMMAND,
            name = {"--delimiter"},
            description = "Delimiter to separate fields in CSV",
            title = "delimiter",
            required = false)
    private String delimiter;

    @SuppressWarnings("FieldCanBeLocal")
    @Option(type = OptionType.COMMAND,
            name = {"--quote"},
            description = "Character to treat as quotation character for values in CSV data",
            title = "quote",
            required = false)
    private String quote;

    @SuppressWarnings("FieldCanBeLocal")
    @Option(type = OptionType.COMMAND,
            name = {"--debug"},
            description = "Print detailed diagnostic output.")
    private boolean debug = false;

    @Override
    public void run()
    {
        try
        {
            Formatting formatting = buildFormatting();

            ConnectionConfig connectionConfig = ConnectionConfig.forDatabase( DatabaseType.MySQL )
                    .host( host )
                    .port( port )
                    .database( database )
                    .username( user )
                    .password( password )
                    .build();

            Environment environment = new EnvironmentSupplier(
                    new ImportToolDirectorySupplier( Paths.get( importToolDirectory ) ),
                    new DestinationDirectorySupplier( Paths.get( destinationDirectory ), force ),
                    new CsvDirectorySupplier( Paths.get( csvRootDirectory ) ) ).supply();

            Callable<CsvResources> createCsvResources = StringUtils.isNotEmpty( csvResourcesUri ) ?
                    CreateCsvResources.fromExistingFile( csvResourcesUri ) :
                    new CreateCsvResources(
                            new CreateCsvResourcesEventHandler(),
                            environment.csvDirectory(),
                            connectionConfig,
                            formatting,
                            new MySqlExportSqlSupplier() );

            new ExportFromMySql(
                    new ExportMySqlEventHandler(),
                    createCsvResources,
                    connectionConfig,
                    formatting,
                    environment ).call();
        }
        catch ( Exception e )
        {
            e.printStackTrace( System.err );
            System.exit( -1 );
        }
    }

    private Formatting buildFormatting() throws IOException
    {

        Map<String, String> importToolOptions = readImportToolOptionsFromFile();

        return Formatting.builder()
                .delimiter( getDelimiter( importToolOptions ) )
                .quote( getQuoteCharacter( importToolOptions ) )
                .build();
    }

    private Map<String, String> readImportToolOptionsFromFile()
    {
        ObjectMapper objectMapper = new ObjectMapper();
        if ( StringUtils.isNotEmpty( importToolOptionsDirectory ) &&
                Files.exists( Paths.get( importToolOptionsDirectory ) ) )
        {
            File path = Paths.get( importToolOptionsDirectory ).toFile();
            try
            {
                return objectMapper.readValue( path, new HashMap<String, String>().getClass() );
            }
            catch ( IOException e )
            {
                Loggers.Default.log( Level.WARNING, "Skipping reading options from file due to error.", e );
                return Collections.emptyMap();
            }
        }
        else
        {
            Loggers.Default.log( Level.INFO, "Skipping reading import options from file. File doesn't exist" );
            return Collections.emptyMap();
        }
    }

    private Delimiter getDelimiter( Map<String, String> importToolOptions )
    {
        if ( StringUtils.isNotEmpty( this.delimiter ) )
        {
            return new Delimiter( this.delimiter );
        }
        else if ( !importToolOptions.isEmpty() )
        {
            return new Delimiter( importToolOptions.get( "delimiter" ) );
        }
        else
        {
            return DEFAULT_DELIMITER;
        }
    }

    private QuoteChar getQuoteCharacter( Map<String, String> importToolOptions )
    {
        if ( StringUtils.isNotEmpty( this.quote ) )
        {
            return new QuoteChar( this.quote, this.quote );
        }
        else if ( !importToolOptions.isEmpty() )
        {
            return new QuoteChar(
                    importToolOptions.get( "quote" ), importToolOptions.get( "quote" ) );
        }
        else
        {
            return DEFAULT_QUOTE_CHAR;
        }
    }

    private static class ExportMySqlEventHandler implements ExportFromMySql.Events
    {
        @Override
        public void onExportingToCsv( Path csvDirectory )
        {
            CliRunner.print( "Exporting from MySQL to CSV..." );
            CliRunner.print( format( "CSV directory: %s", csvDirectory ) );
        }

        @Override
        public void onCreatingNeo4jStore()
        {
            CliRunner.print( "Creating Neo4j store from CSV..." );
        }

        @Override
        public void onExportComplete( Path destinationDirectory )
        {
            CliRunner.print( "Done" );
            CliRunner.printResult( destinationDirectory );
        }
    }

    private static class CreateCsvResourcesEventHandler implements CreateCsvResources.Events
    {

        @Override
        public void onCreatingCsvMappings()
        {
            CliRunner.print( "Creating MySQL to CSV mappings..." );
        }

        @Override
        public void onMappingsCreated( Path mappingsFile )
        {
            CliRunner.printResult( mappingsFile );
        }
    }
}
