package de.pritcloud.rideadent;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class LogicTest {
    @Test
    public void multipleDevices() {
        assertTrue(
                Logic.shouldRun(
                        Set.of("A", "B"),
                        Set.of("B", "C")));

        assertFalse(
                Logic.shouldRun(
                        Set.of("A"),
                        Set.of("B")));
    }

    @Test
    public void bluetoothRadioOffCannotUsePreviouslyRecordedConnections() {
        Set<String> recorded = Set.of("A", "B");
        Set<String> chosen = Set.of("B");
        assertTrue(Logic.shouldRun(Logic.effectiveActive(recorded, true), chosen));
        assertFalse(Logic.shouldRun(Logic.effectiveActive(recorded, false), chosen));
        assertTrue(Logic.effectiveActive(recorded, false).isEmpty());
        assertEquals(recorded, Logic.effectiveActive(recorded, true));
        assertEquals(recorded, Set.of("A", "B"));
    }

    @Test
    public void callAutomationRequiresRuntimeEnabledAndConnected() {
        assertFalse(
                Logic.shouldRejectCall(
                        false, true, true));

        assertFalse(
                Logic.shouldRejectCall(
                        true, false, true));

        assertFalse(
                Logic.shouldRejectCall(
                        true, true, false));

        assertTrue(
                Logic.shouldRejectCall(
                        true, true, true));
    }

    @Test
    public void cooldownDoesNotSuppressRejection() {
        assertTrue(
                Logic.shouldRejectCall(
                        true, true, true));

        assertFalse(
                Logic.smsCooldownElapsed(
                        1_000_000L,
                        1_000_001L));

        assertTrue(
                Logic.shouldRejectCall(
                        true, true, true));

        assertTrue(
                Logic.smsCooldownElapsed(
                        1_000_000L,
                        1_600_000L));

        assertTrue(
                Logic.smsCooldownElapsed(
                        0L,
                        1_000_001L));
    }

    @Test
    public void blitzerWatchRequiresRuntimeEnabledAndSelectedBt() {
        assertTrue(
                Logic.shouldWatchBlitzer(
                        true, true, true));

        assertFalse(
                Logic.shouldWatchBlitzer(
                        false, true, true));

        assertFalse(
                Logic.shouldWatchBlitzer(
                        true, false, true));

        assertFalse(
                Logic.shouldWatchBlitzer(
                        true, true, false));
    }

    @Test
    public void driveServiceRunsForEitherDrivingFunction() {
        assertTrue(
                Logic.shouldRunDriveService(
                        true,
                        true, true,
                        false, false));

        assertTrue(
                Logic.shouldRunDriveService(
                        true,
                        false, false,
                        true, true));

        assertFalse(
                Logic.shouldRunDriveService(
                        false,
                        true, true,
                        true, true));

        assertFalse(
                Logic.shouldRunDriveService(
                        true,
                        true, false,
                        true, false));
    }

    @Test
    public void blitzerToggleStartsAndStopsWhenDeviceIsConnected() {
        assertTrue(Logic.shouldSendBlitzerToggleCommand(true, true, false, true));
        assertTrue(Logic.shouldSendBlitzerToggleCommand(true, true, true, false));
        assertFalse(Logic.shouldSendBlitzerToggleCommand(true, true, true, true));
        assertFalse(Logic.shouldSendBlitzerToggleCommand(true, true, false, false));
    }

    @Test
    public void blitzerToggleDoesNotCommandWithoutRuntimeOrConnection() {
        assertFalse(Logic.shouldSendBlitzerToggleCommand(true, false, true, false));
        assertFalse(Logic.shouldSendBlitzerToggleCommand(true, false, false, true));
        assertFalse(Logic.shouldSendBlitzerToggleCommand(false, true, true, false));
        assertFalse(Logic.shouldSendBlitzerToggleCommand(false, true, false, true));
    }

    @Test
    public void blitzerAndCallsCanRemainIndependentlyActive() {
        assertTrue(Logic.shouldRunDriveService(true, false, true, true, true));
        assertTrue(Logic.shouldRunDriveService(true, true, true, false, true));
        assertFalse(Logic.shouldRunDriveService(true, false, true, false, true));
    }

    @Test
    public void absenceAllowsRestartWithoutPreviousNotification() {
        assertTrue(
                Logic.shouldRestartMissingNotification(
                        true,
                        true,
                        true,
                        false,
                        15_000L,
                        0L));

        assertFalse(
                Logic.shouldRestartMissingNotification(
                        false,
                        true,
                        true,
                        false,
                        15_000L,
                        0L));

        assertFalse(
                Logic.shouldRestartMissingNotification(
                        true,
                        true,
                        true,
                        true,
                        15_000L,
                        0L));

        assertFalse(
                Logic.shouldRestartMissingNotification(
                        true,
                        true,
                        false,
                        false,
                        15_000L,
                        0L));

        assertFalse(
                Logic.shouldRestartMissingNotification(
                        true,
                        false,
                        true,
                        false,
                        15_000L,
                        0L));

        assertFalse(
                Logic.shouldRestartMissingNotification(
                        true,
                        true,
                        true,
                        false,
                        30_000L,
                        60_000L));

        assertTrue(
                Logic.shouldRestartMissingNotification(
                        true,
                        true,
                        true,
                        false,
                        60_000L,
                        60_000L));
    }
}
