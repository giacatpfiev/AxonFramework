/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.saga;

import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.junit.*;

import java.time.Duration;
import java.time.Instant;

import static org.axonframework.eventhandling.saga.SagaLifecycle.cancelDeadline;
import static org.axonframework.eventhandling.saga.SagaLifecycle.scheduleDeadline;

/**
 * Tests for scheduling deadlines on {@link SagaTestFixture}.
 *
 * @author Milan Savic
 */
public class SagaDeadlineSchedulingTest {

    private static final int TRIGGER_DURATION_MINUTES = 10;

    private SagaTestFixture<MySaga> fixture;

    @Before
    public void setUp() {
        fixture = new SagaTestFixture<>(MySaga.class);
    }

    @Test
    public void testDeadlineScheduling() {
        fixture.givenNoPriorActivity()
               .whenAggregate("id").publishes(new TriggerSagaStartEvent("id"))
               .expectActiveSagas(1)
               .expectScheduledDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "deadlineDetails")
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineSchedulingTypeMatching() {
        fixture.givenNoPriorActivity()
               .whenAggregate("id").publishes(new TriggerSagaStartEvent("id"))
               .expectActiveSagas(1)
               .expectScheduledDeadlineOfType(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), String.class)
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineMet() {
        fixture.givenAggregate("id").published(new TriggerSagaStartEvent("id"))
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectActiveSagas(1)
               .expectDeadlinesMet("deadlineDetails")
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineCancelled() {
        fixture.givenAggregate("id")
               .published(new TriggerSagaStartEvent("id"))
               .whenPublishingA(new ResetTriggerEvent("id"))
               .expectActiveSagas(1)
               .expectNoScheduledDeadlines()
               .expectNoScheduledEvents();
    }

    @SuppressWarnings("unused")
    public static class MySaga {

        private ScheduleToken scheduleToken;

        @StartSaga
        @SagaEventHandler(associationProperty = "identifier")
        public void handleSagaStart(TriggerSagaStartEvent event, @Timestamp Instant timestamp) {
            scheduleToken = scheduleDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES),
                                             "deadlineDetails");
        }

        @SagaEventHandler(associationProperty = "identifier")
        public void handleResetTriggerEvent(ResetTriggerEvent event) {
            cancelDeadline(scheduleToken);
        }

        @DeadlineHandler
        public void handleDeadline(String deadlineInfo) {

        }
    }
}
