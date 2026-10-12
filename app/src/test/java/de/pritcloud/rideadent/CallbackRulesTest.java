package de.pritcloud.rideadent;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallbackRulesTest {
    @Test public void acceptsOrdinaryPhoneNumbers() {
        assertEquals("+4930123456", CallbackRules.dialNumber("+49 (30) 123-456"));
        assertEquals("03012345", CallbackRules.dialNumber("030 123-45"));
        assertEquals("1234", CallbackRules.dialNumber("1234"));
    }
    @Test public void rejectsDangerousOrUncallableTargets() {
        assertNull(CallbackRules.dialNumber(null));
        assertNull(CallbackRules.dialNumber(""));
        assertNull(CallbackRules.dialNumber("12"));
        assertNull(CallbackRules.dialNumber("112"));
        assertNull(CallbackRules.dialNumber("*123#"));
        assertNull(CallbackRules.dialNumber("+49#0"));
        assertNull(CallbackRules.dialNumber("sip:user@example.com"));
        assertNull(CallbackRules.dialNumber("0049;123"));
        assertNull(CallbackRules.dialNumber("++491234"));
        assertNull(CallbackRules.dialNumber("１２３４５"));
    }
}
