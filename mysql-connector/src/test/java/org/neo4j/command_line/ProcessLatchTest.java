package org.neo4j.command_line;

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;

import org.neo4j.utils.ExecutionTimer;
import org.neo4j.utils.ResourceRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProcessLatchTest
{
    private static final String NEWLINE = System.lineSeparator();

    @Rule
    public final ResourceRule<CommandFactory> commandFactory = new ResourceRule<>( CommandFactory.newFactory() );

    @Test
    public void shouldReturnOkWhenPredicateSatisfied() throws Exception
    {
        // given
        String expectedStreamContents = "1" + NEWLINE + "2" + NEWLINE + "3";

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

        ExecutionTimer timer = ExecutionTimer.startTimer();

        try ( ResultHandle ignored = commands.execute() )
        {
            // when
            ProcessLatch.ProcessLatchResult result = latch.awaitContents( 5, TimeUnit.SECONDS );
            long duration = timer.duration();

            // then
            assertTrue( result.ok() );
            assertEquals( expectedStreamContents, result.streamContents() );
            assertTrue( duration < TimeUnit.SECONDS.toMillis( 3 ) );
        }
    }

    @Test
    public void shouldReturnNotOkWhenPredicateNotSatisfied() throws Exception
    {
        // given
        String expectedStreamContents = "1" + NEWLINE + "2" + NEWLINE + "3";

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

        ExecutionTimer timer = ExecutionTimer.startTimer();

        try ( ResultHandle ignored = commands.execute() )
        {
            // when
            ProcessLatch.ProcessLatchResult result = latch.awaitContents( 3, TimeUnit.SECONDS );
            long duration = timer.duration();

            // then
            assertFalse( result.ok() );
            assertEquals( expectedStreamContents, result.streamContents() );
            assertTrue( duration >= TimeUnit.SECONDS.toMillis( 3 ) );
        }
    }

    @Test
    public void shouldThrowExceptionIfPredicateThrowsException() throws Exception
    {
        // given
        IllegalArgumentException expectedException = new IllegalArgumentException( "Illegal value: 5" );

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

        ExecutionTimer timer = ExecutionTimer.startTimer();

        try ( ResultHandle ignored = commands.execute() )
        {
            // when
            latch.awaitContents( 4, TimeUnit.SECONDS );
            fail( "Expected IllegalArgumentException" );
        }
        catch ( IllegalArgumentException e )
        {
            // then
            assertEquals( expectedException, e );
            assertTrue( timer.duration() < TimeUnit.SECONDS.toMillis( 2 ) );
        }
    }
}
