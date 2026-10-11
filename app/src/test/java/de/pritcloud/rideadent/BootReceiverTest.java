package de.pritcloud.rideadent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BootReceiverTest {
    @Test
    public void bootRequiresAutoStart() {
        assertFalse(
                BootReceiver.runtimeAfterEvent(
                        "android.intent.action.BOOT_COMPLETED",
                        false,
                        true));

        assertTrue(
                BootReceiver.runtimeAfterEvent(
                        "android.intent.action.BOOT_COMPLETED",
                        true,
                        false));
    }

    @Test
    public void packageUpdatePreservesRuntimeState() {
        assertTrue(
                BootReceiver.runtimeAfterEvent(
                        "android.intent.action.MY_PACKAGE_REPLACED",
                        false,
                        true));

        assertFalse(
                BootReceiver.runtimeAfterEvent(
                        "android.intent.action.MY_PACKAGE_REPLACED",
                        true,
                        false));
    }

    @Test
    public void unrelatedEventKeepsPreviousState() {
        assertTrue(
                BootReceiver.runtimeAfterEvent(
                        "example.UNRELATED",
                        false,
                        true));

        assertFalse(
                BootReceiver.runtimeAfterEvent(
                        "example.UNRELATED",
                        true,
                        false));
    }
}
