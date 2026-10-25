package de.pritcloud.rideadent;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BlitzerStopRulesTest {
    @Test public void reconcilesOnlyStateTransitions() {
        assertTrue(BlitzerStopRules.reconcile(true, false));
        assertTrue(BlitzerStopRules.reconcile(false, true));
        assertFalse(BlitzerStopRules.reconcile(true, true));
        assertFalse(BlitzerStopRules.reconcile(false, false));
    }

    @Test public void restartAlwaysCancelsLateStop() {
        for (int count = 0; count <= 3; count++) {
            assertEquals(BlitzerStopRules.Action.CANCEL_RESTARTED,
                    BlitzerStopRules.evaluate(true, true, count));
        }
    }

    @Test public void distinguishesConfirmedUnknownAndFailed() {
        assertEquals(BlitzerStopRules.Action.CONFIRMED,
                BlitzerStopRules.evaluate(false, false, 0));
        assertEquals(BlitzerStopRules.Action.UNKNOWN,
                BlitzerStopRules.evaluate(false, null, 0));
        assertEquals(BlitzerStopRules.Action.RETRY,
                BlitzerStopRules.evaluate(false, true, 0));
        assertEquals(BlitzerStopRules.Action.RETRY,
                BlitzerStopRules.evaluate(false, true, 1));
        assertEquals(BlitzerStopRules.Action.NOT_CONFIRMED,
                BlitzerStopRules.evaluate(false, true, 2));
    }
}
