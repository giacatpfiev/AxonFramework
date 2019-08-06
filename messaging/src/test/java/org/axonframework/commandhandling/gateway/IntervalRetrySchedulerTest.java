package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.jdbc.JdbcException;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.*;

/**
 * Test the {@link IntervalRetryScheduler}.
 *
 * @author Bert Laverman
 */
public class IntervalRetrySchedulerTest {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int RETRY_INTERVAL = 100;
    private static final int MAX_RETRIES = 5;

    private IntervalRetryScheduler retryScheduler;

    @Before
    public void setup() {
        retryScheduler = IntervalRetryScheduler
                .builder()
                .retryExecutor(new ScheduledThreadPoolExecutor(1))
                .retryInterval(RETRY_INTERVAL)
                .maxRetryCount(MAX_RETRIES)
                .build();
    }

    /**
     * Schedule a retry, faking that we had a certain nr of (transient) failures.
     *
     * @param retryScheduler the scheduler to use.
     * @param nrOfFailures   the number of (transient) failures .
     * @return the nr of milliseconds delay actually achieved.
     */
    static long doScheduleRetry(RetryScheduler retryScheduler, int nrOfFailures) {
        final CommandMessage<?> msg = GenericCommandMessage.asCommandMessage("Hello world");
        final Instant before = Instant.now();
        final FutureTask<Instant> after = new FutureTask<>(Instant::now);

        final JdbcException exc = new JdbcException("Exception", new NullPointerException());
        List<Class<? extends Throwable>[]> failures = new ArrayList<>();
        for (int i = 0; i < nrOfFailures; i++) {
            Class<?>[] arr = new Class[2];
            arr[0] = JdbcException.class;
            arr[1] = NullPointerException.class;
            failures.add((Class<? extends Throwable>[]) arr);
        }
        if (retryScheduler.scheduleRetry(msg, exc, failures, after)) {
            try {
                final Instant afterInstant = after.get();
                logger.info("scheduleRetry(): Actual delay was {}ms. (nr of failures {} out of max {})",
                            afterInstant.toEpochMilli() - before.toEpochMilli(),
                            nrOfFailures, MAX_RETRIES);

                return afterInstant.toEpochMilli() - before.toEpochMilli();
            } catch (InterruptedException ex) {
                fail("Test failed: somebody interrupted us.");
            } catch (ExecutionException ex) {
                fail("Test failed: An exception occurred where none should be.");
            }
        }
        return 0;
    }

    @Test
    public void scheduleRetry() {
        for (int nrOfFailures = 1; nrOfFailures <= MAX_RETRIES; nrOfFailures++) {
            assertTrue("Scheduling a retry should wait the required delay.",
                       doScheduleRetry(retryScheduler, nrOfFailures) >= RETRY_INTERVAL);
        }

        assertEquals("Scheduling a retry when past maxRetryCount should have failed.",
                     0, doScheduleRetry(retryScheduler, MAX_RETRIES + 1));
    }
}