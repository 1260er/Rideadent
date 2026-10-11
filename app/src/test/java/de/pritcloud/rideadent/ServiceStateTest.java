package de.pritcloud.rideadent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ServiceStateTest {
    @Test
    public void heartbeatIsFreshForAtMost45Seconds() {
        assertTrue(
                ServiceState.isFreshAt(
                        1_000L,
                        1_000L));

        assertTrue(
                ServiceState.isFreshAt(
                        1_000L,
                        46_000L));

        assertFalse(
                ServiceState.isFreshAt(
                        1_000L,
                        46_001L));
    }

    @Test
    public void missingOrFutureHeartbeatIsNotFresh() {
        assertFalse(
                ServiceState.isFreshAt(
                        0L,
                        10_000L));

        assertFalse(
                ServiceState.isFreshAt(
                        20_000L,
                        10_000L));
    }
}
