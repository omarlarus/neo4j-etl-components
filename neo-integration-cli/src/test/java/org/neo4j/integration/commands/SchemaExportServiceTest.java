package org.neo4j.integration.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.junit.Test;

import org.neo4j.integration.io.AwaitHandle;
import org.neo4j.integration.sql.DatabaseClient;
import org.neo4j.integration.sql.QueryResults;
import org.neo4j.integration.sql.StubQueryResults;
import org.neo4j.integration.sql.metadata.JoinTable;
import org.neo4j.integration.sql.metadata.TableName;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaExportServiceTest
{

    private SchemaExportService schemaExportService = new SchemaExportService();

    @Test
    public void exportSchemaShouldExportTablesAndJoinsForTwoTableJoin() throws Exception
    {
        // given
        QueryResults personResults = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "DATA_TYPE", "COLUMN_TYPE" )
                .addRow( "id", "INT", "PrimaryKey" )
                .addRow( "username", "TEXT", "Data" )
                .addRow( "addressId", "int", "ForeignKey" )
                .build();

        QueryResults addressResults = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "DATA_TYPE", "COLUMN_TYPE" )
                .addRow( "id", "INT", "PrimaryKey" )
                .addRow( "postcode", "TEXT", "Data" )
                .build();

        QueryResults joinResults = StubQueryResults.builder()
                .columns( "SOURCE_TABLE_SCHEMA",
                        "SOURCE_TABLE_NAME",
                        "SOURCE_COLUMN_NAME",
                        "SOURCE_COLUMN_TYPE",
                        "TARGET_TABLE_SCHEMA",
                        "TARGET_TABLE_NAME",
                        "TARGET_COLUMN_NAME",
                        "TARGET_COLUMN_TYPE" )
                .addRow( "test", "Person", "id", "PrimaryKey", "test", "Person", "id", "PrimaryKey" )
                .addRow( "test", "Person", "addressId", "ForeignKey", "test", "Address", "id", "PrimaryKey" )
                .build();


        DatabaseClient databaseClient = mock( DatabaseClient.class );
        when( databaseClient.executeQuery( any( String.class ) ) )
                .thenReturn( AwaitHandle.forReturnValue( personResults ) )
                .thenReturn( AwaitHandle.forReturnValue( addressResults ) )
                .thenReturn( AwaitHandle.forReturnValue( joinResults ) );

        // when
        SchemaExport schemaExport = schemaExportService.doExport(
                new SchemaDetails( "test", "Person", "Address", Optional.empty() ),
                () -> databaseClient );

        // then
        assertEquals( 1, schemaExport.startTable().stream()
                .filter( t -> t.name().fullName().equals( "test.Person" ) )
                .count() );
        assertEquals( 1, schemaExport.endTable().stream()
                .filter( t -> t.name().fullName().equals( "test.Address" ) )
                .count() );
        assertEquals( asList( new TableName( "test.Person" ), new TableName( "test.Address" ) ),
                schemaExport.joins().stream().findFirst().get().tableNames() );

        assertTrue( schemaExport.joinTables().isEmpty() );
    }

    @Test
    public void exportSchemaShouldExportTablesAndJoinsForThreeTableJoin() throws Exception
    {
        // given
        QueryResults studentResults = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "DATA_TYPE", "COLUMN_TYPE" )
                .addRow( "id", "INT", "PrimaryKey" )
                .addRow( "username", "TEXT", "Data" )
                .build();

        QueryResults courseResults = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "DATA_TYPE", "COLUMN_TYPE" )
                .addRow( "id", "INT", "PrimaryKey" )
                .addRow( "name", "TEXT", "Data" )
                .build();

        QueryResults joinTableResults = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "DATA_TYPE", "COLUMN_TYPE" )
                .addRow( "studentId", "INT", "ForeignKey" )
                .addRow( "courseId", "INT", "ForeignKey" )
                .build();

        QueryResults joinResults = StubQueryResults.builder()
                .columns( "SOURCE_TABLE_SCHEMA",
                        "SOURCE_TABLE_NAME",
                        "SOURCE_COLUMN_NAME",
                        "SOURCE_COLUMN_TYPE",
                        "TARGET_TABLE_SCHEMA",
                        "TARGET_TABLE_NAME",
                        "TARGET_COLUMN_NAME",
                        "TARGET_COLUMN_TYPE" )
                .addRow( "test", "Student_Course", "studentId", "ForeignKey", "test", "Student", "id", "PrimaryKey" )
                .addRow( "test", "Student_Course", "courseId", "ForeignKey", "test", "Course", "id", "PrimaryKey" )
                .build();

        DatabaseClient databaseClient = mock( DatabaseClient.class );
        when( databaseClient.executeQuery( any( String.class ) ) )
                .thenReturn( AwaitHandle.forReturnValue( studentResults ) )
                .thenReturn( AwaitHandle.forReturnValue( courseResults ) )
                .thenReturn( AwaitHandle.forReturnValue( joinResults ) )
                .thenReturn( AwaitHandle.forReturnValue( joinTableResults ) );

        // when
        SchemaExport schemaExport = schemaExportService.doExport(
                new SchemaDetails( "test", "Student", "Course", Optional.of( "Student_Course" ) ),
                () -> databaseClient );

        // then
        assertEquals( 1, schemaExport.startTable().stream()
                .filter( t -> t.name().fullName().equals( "test.Student" ) )
                .count() );
        assertEquals( 1, schemaExport.endTable().stream()
                .filter( t -> t.name().fullName().equals( "test.Course" ) )
                .count() );
        assertTrue( schemaExport.joins().isEmpty() );
        assertEquals(
                asList( "test.Student_Course.studentId",
                        "test.Student.id",
                        "test.Student_Course.courseId",
                        "test.Course.id" ),
                keyNames( schemaExport.joinTables().stream().findFirst().get() ) );
    }

    private Collection<String> keyNames( JoinTable table )
    {
        Collection<String> results = new ArrayList<>();
        results.add( table.join().left().source().name() );
        results.add( table.join().left().target().name() );
        results.add( table.join().right().source().name() );
        results.add( table.join().right().target().name() );
        return results;
    }

}
