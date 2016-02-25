package org.neo4j.integration;

import java.nio.file.Path;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import org.neo4j.integration.mysql.MySqlClient;
import org.neo4j.integration.neo4j.Neo4j;
import org.neo4j.integration.neo4j.Neo4jVersion;
import org.neo4j.integration.provisioning.Neo4jFixture;
import org.neo4j.integration.provisioning.Server;
import org.neo4j.integration.provisioning.ServerFixture;
import org.neo4j.integration.provisioning.scripts.MySqlScripts;
import org.neo4j.integration.sql.DatabaseType;
import org.neo4j.integration.util.ResourceRule;
import org.neo4j.integration.util.Strings;
import org.neo4j.integration.util.TemporaryDirectory;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;

public class ExportFromMySqlIntegrationTest
{
    private static final Neo4jVersion NEO4J_VERSION = Neo4jVersion.v3_0_0_M03;

    @ClassRule
    public static final ResourceRule<Path> tempDirectory = new ResourceRule<>( TemporaryDirectory.temporaryDirectory() );

    @ClassRule
    public static final ResourceRule<Server> mySqlServer = new ResourceRule<>(
            ServerFixture.server(
                    "mysql-integration-test",
                    DatabaseType.MySQL.defaultPort(),
                    MySqlScripts.startupScript(),
                    tempDirectory.get() ) );

    @ClassRule
    public static final ResourceRule<Neo4j> neo4j = new ResourceRule<>(
            Neo4jFixture.neo4j( NEO4J_VERSION, tempDirectory.get() ) );

    @BeforeClass
    public static void setUp() throws Exception
    {
        populateMySqlDatabase();
    }

    @Test
    public void shouldExportFromMySqlAndImportIntoGraph() throws Exception
    {
        // when
        exportFromMySqlToNeo4j( "Person", "Address" );

        // then
        neo4j.get().start();

        String expectedResults = Strings.lineSeparated(
                "+-------------------------------+",
                "| n                             |",
                "+-------------------------------+",
                "| Node[0]{username:\"user-1\"}    |",
                "| Node[1]{username:\"user-2\"}    |",
                "| Node[2]{username:\"user-3\"}    |",
                "| Node[3]{username:\"user-4\"}    |",
                "| Node[4]{username:\"user-5\"}    |",
                "| Node[5]{username:\"user-6\"}    |",
                "| Node[6]{username:\"user-7\"}    |",
                "| Node[7]{username:\"user-8\"}    |",
                "| Node[8]{username:\"user-9\"}    |",
                "| Node[9]{postcode:\"AB12 1XY\"}  |",
                "| Node[10]{postcode:\"XY98 9BA\"} |",
                "| Node[11]{postcode:\"ZZ1 0MN\"}  |",
                "+-------------------------------+" );

        String execute = neo4j.get().execute( "MATCH (n) RETURN n;" );
        assertThat( execute, startsWith( expectedResults ) );
        neo4j.get().stop();
    }

    @Test
    public void shouldExportFromMySqlAndImportIntoGraphForNumericTables() throws Exception
    {
        // when
        exportFromMySqlToNeo4j( "String_Table", "Numeric_Table" );

        // then
        neo4j.get().start();

        String expectedResults = Strings.lineSeparated(
                "+--------------------------------------------------------------------------------------------------------------------------------------------------+",
                "| n                                                                                                                                                |",
                "+--------------------------------------------------------------------------------------------------------------------------------------------------+",
                "| Node[0]{char_field:\"char-field\",varchar_field:\"varchar-field\"}                                                                                   |",
                "| Node[1]{tinyint_field:1,smallint_field:123,mediumint_field:123,bigint_field:123,float_field:123.2,double_field:1.232343445E7,decimal_field:18.0} |",
                "+--------------------------------------------------------------------------------------------------------------------------------------------------+" );

        String execute = neo4j.get().execute( "MATCH (n) RETURN n;" );
        assertThat( execute, startsWith( expectedResults ) );
        neo4j.get().stop();
    }

    private void exportFromMySqlToNeo4j( String parent, String child )
    {
        NeoIntegrationCli.executeMainReturnSysOut(
                new String[]{"mysql-export",
                        "--host", mySqlServer.get().ipAddress(),
                        "--user", MySqlClient.Parameters.DBUser.value(),
                        "--password", MySqlClient.Parameters.DBPassword.value(),
                        "--database", MySqlClient.Parameters.Database.value(),
                        "--import-tool", neo4j.get().binDirectory().toString(),
                        "--csv-directory", tempDirectory.get().toString(),
                        "--destination", neo4j.get().databasesDirectory().resolve( "graph.db" ).toString(),
                        "--parent", parent,
                        "--child", child} );
    }

    private static void populateMySqlDatabase() throws Exception
    {
        MySqlClient client = new MySqlClient( mySqlServer.get().ipAddress() );
        client.execute( MySqlScripts.setupDatabaseScript().value() );
    }
}
