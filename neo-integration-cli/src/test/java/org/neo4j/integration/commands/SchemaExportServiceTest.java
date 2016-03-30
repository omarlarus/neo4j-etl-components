package org.neo4j.integration.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import org.neo4j.integration.io.AwaitHandle;
import org.neo4j.integration.sql.DatabaseClient;
import org.neo4j.integration.sql.QueryResults;
import org.neo4j.integration.sql.StubQueryResults;
import org.neo4j.integration.sql.metadata.JoinTable;
import org.neo4j.integration.sql.metadata.Table;
import org.neo4j.integration.sql.metadata.TableName;

import static java.util.Arrays.asList;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaExportServiceTest
{

    private DatabaseClient databaseClient = mock( DatabaseClient.class );
    private SchemaExportService schemaExportService = new SchemaExportService( databaseClient );

    @Test
    public void exportSchemaShouldExportTablesAndJoinsForTwoTableJoin() throws Exception
    {
        // given
        QueryResults personTableSchema = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "REFERENCED_TABLE_NAME", "COLUMN_KEY" )
                .addRow( "id", null, "PRI" )
                .addRow( "username", null, "Data" )
                .addRow( "addressId", "Address", "MUL" )
                .build();


        QueryResults personResults = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "DATA_TYPE", "COLUMN_TYPE" )
                .addRow( "id", "INT", "PrimaryKey" )
                .addRow( "username", "TEXT", "Data" )
                .addRow( "addressId", "int", "ForeignKey" )
                .build();

        QueryResults addressTableSchema = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "REFERENCED_TABLE_NAME", "COLUMN_KEY" )
                .addRow( "id", null, "PRI" )
                .addRow( "postcode", null, "Data" )
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


        TableName person = new TableName( "test.Person" );
        TableName address = new TableName( "test.Address" );

        when( databaseClient.tableNames() ).thenReturn( asList( person, address ) );
        when( databaseClient.executeQuery( any( String.class ) ) )
                .thenReturn( AwaitHandle.forReturnValue( personTableSchema ) )
                .thenReturn( AwaitHandle.forReturnValue( personResults ) )
                .thenReturn( AwaitHandle.forReturnValue( joinResults ) )
                .thenReturn( AwaitHandle.forReturnValue( addressTableSchema ) )
                .thenReturn( AwaitHandle.forReturnValue( addressResults ) );

        // when
        SchemaExport schemaExport = schemaExportService.inspect();

        // then
        List<TableName> tableNames = schemaExport.tables().stream().map( Table::name ).collect( Collectors.toList() );

        assertThat( tableNames, hasItems( person, address ) );
        assertThat( schemaExport.joins().stream().findFirst().get().tableNames(), hasItems( person, address ) );
        assertTrue( schemaExport.joinTables().isEmpty() );
    }

    @Test
    public void exportSchemaShouldExportTablesAndJoinsForThreeTableJoin() throws Exception
    {
        // given
        QueryResults studentTableSchema = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "REFERENCED_TABLE_NAME", "COLUMN_KEY" )
                .addRow( "id", null, "PRI" )
                .addRow( "username", null, "Data" )
                .build();

        QueryResults studentResults = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "DATA_TYPE", "COLUMN_TYPE" )
                .addRow( "id", "INT", "PrimaryKey" )
                .addRow( "username", "TEXT", "Data" )
                .build();

        QueryResults courseTableSchema = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "REFERENCED_TABLE_NAME", "COLUMN_KEY" )
                .addRow( "id", null, "PRI" )
                .addRow( "name", null, "Data" )
                .build();

        QueryResults courseResults = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "DATA_TYPE", "COLUMN_TYPE" )
                .addRow( "id", "INT", "PrimaryKey" )
                .addRow( "name", "TEXT", "Data" )
                .build();

        QueryResults joinTableSchema = StubQueryResults.builder()
                .columns( "COLUMN_NAME", "REFERENCED_TABLE_NAME", "COLUMN_KEY" )
                .addRow( "studentId", "Student", "MUL" )
                .addRow( "courseId", "Course", "MUL" )
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

        TableName student = new TableName( "test.Student" );
        TableName course = new TableName( "test.Course" );
        TableName studentCourse = new TableName( "test.Student_Course" );
        when( databaseClient.tableNames() ).thenReturn( asList( student, course, studentCourse ) );
        when( databaseClient.executeQuery( any( String.class ) ) )
                .thenReturn( AwaitHandle.forReturnValue( studentTableSchema ) )
                .thenReturn( AwaitHandle.forReturnValue( studentResults ) )
                .thenReturn( AwaitHandle.forReturnValue( courseTableSchema ) )
                .thenReturn( AwaitHandle.forReturnValue( courseResults ) )
                .thenReturn( AwaitHandle.forReturnValue( joinTableSchema ) )
                .thenReturn( AwaitHandle.forReturnValue( joinResults ) )
                .thenReturn( AwaitHandle.forReturnValue( joinTableResults ) );

        // when
        SchemaExport schemaExport = schemaExportService.inspect();

        // then
        List<TableName> tableNames = schemaExport.tables().stream().map( Table::name ).collect( Collectors.toList() );

        assertThat( tableNames, hasItems( student, course ) );
        assertTrue( schemaExport.joins().isEmpty() );

        JoinTable joinTable = new ArrayList<>( schemaExport.joinTables() ).get( 0 );
        assertEquals(
                asList( "test.Student_Course.studentId",
                        "test.Student.id",
                        "test.Student_Course.courseId",
                        "test.Course.id" ),
                keyNames( joinTable ) );
    }

    private Collection<String> keyNames( JoinTable table )
    {
        Collection<String> results = new ArrayList<>();
        results.add( table.join().keyTwoSourceColumn().name() );
        results.add( table.join().keyTwoTargetColumn().name() );
        results.add( table.join().keyOneSourceColumn().name() );
        results.add( table.join().keyOneTargetColumn().name() );
        return results;
    }
}
