package org.neo4j.command_line;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;

import org.neo4j.utils.ResourceRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProcessLatchTest
{
    @Rule
    public final ResourceRule<CommandFactory> commandFactory = new ResourceRule<>( CommandFactory.newFactory() );

    @Test
    public void shouldReturnOkWhenPredicateSatisfied() throws Exception
    {
        // given
        ProcessLatch latch = new ProcessLatch( l -> {
            try
            {
                return Integer.valueOf( l ) == 3;
            }
            catch ( NumberFormatException e )
            {
                return false;
            }
        } );

        Commands commands = Commands.forCommands( commandFactory.get().printNumbers( 10 ).commands() )
                .inheritWorkingDirectory()
                .failOnNonZeroExitValue()
                .noTimeout()
                .inheritEnvironment()
                .redirectStdOutTo( latch )
                .build();

        long startTime = System.currentTimeMillis();

        try ( ResultHandle ignored = commands.execute() )
        {
            // when
            ProcessLatch.ProcessLatchResult result = latch.awaitContents( 5, TimeUnit.SECONDS );
            long endTime = System.currentTimeMillis();

            // then
            assertTrue( result.ok() );
            assertTrue( endTime - startTime < TimeUnit.SECONDS.toMillis( 3 ) );
        }
    }

    @Test
    public void shouldReturnNotOkWhenPredicateNotSatisfied() throws Exception
    {
        // given
        ProcessLatch latch = new ProcessLatch( l -> {
            try
            {
                return l.equals( "X" );
            }
            catch ( NumberFormatException e )
            {
                return false;
            }
        } );

        Commands commands = Commands.forCommands( commandFactory.get().printNumbers( 3 ).commands() )
                .inheritWorkingDirectory()
                .failOnNonZeroExitValue()
                .noTimeout()
                .inheritEnvironment()
                .redirectStdOutTo( latch )
                .build();

        long startTime = System.currentTimeMillis();

        try ( ResultHandle ignored = commands.execute() )
        {
            // when
            ProcessLatch.ProcessLatchResult result = latch.awaitContents( 3, TimeUnit.SECONDS );
            long endTime = System.currentTimeMillis();

            // then
            assertFalse( result.ok() );
            assertTrue( endTime - startTime >= TimeUnit.SECONDS.toMillis( 3 ) );
        }
    }

    @Test
    public void shouldThrowExceptionWhenPredicateThrowsException() throws Exception
    {
        // given
        IOException expectedException = new IOException( "Illegal value: 5" );

        ProcessLatch latch = new ProcessLatch( l -> {
            try
            {
                if ( Integer.valueOf( l ) == 2 )
                {
                    throw expectedException;
                }
                return Integer.valueOf( l ) == 6;
            }
            catch ( NumberFormatException e )
            {
                return false;
            }
        } );

        Commands commands = Commands.forCommands( commandFactory.get().printNumbers( 10 ).commands() )
                .inheritWorkingDirectory()
                .failOnNonZeroExitValue()
                .noTimeout()
                .inheritEnvironment()
                .redirectStdOutTo( latch )
                .build();

        long startTime = System.currentTimeMillis();

        try ( ResultHandle ignored = commands.execute() )
        {
            // when
            latch.awaitContents( 4, TimeUnit.SECONDS );
            fail( "Expected IOException" );
        }
        catch ( IOException e )
        {
            // then
            assertEquals( expectedException, e );

            long endTime = System.currentTimeMillis();
            assertTrue( endTime - startTime < TimeUnit.SECONDS.toMillis( 2 ) );
        }
    }
}
