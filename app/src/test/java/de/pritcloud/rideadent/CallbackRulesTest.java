package de.pritcloud.rideadent;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallbackRulesTest {
    @Test public void acceptsOrdinaryPhoneNumbers() {
        assertEquals("+4930123456", CallbackRules.dialNumber("+49 (30) 123-456"));
        assertEquals("03012345", CallbackRules.dialNumber("030 123-45"));
        assertEquals("1234", CallbackRules.dialNumber("1234"));
    }
    @Test public void comparesSameCallerAcrossFormats() {
        assertTrue(CallbackRules.sameNumber("+49 (30) 123-456", "+4930123456"));
        assertTrue(CallbackRules.sameNumber("0049 30 123456", "+49-30-123456"));
        assertTrue(CallbackRules.sameNumber("030 123-456", "030123456"));
        assertFalse(CallbackRules.sameNumber("030123456", "030123457"));
        assertFalse(CallbackRules.sameNumber("112", "112"));
        assertFalse(CallbackRules.sameNumber(null, "+4930123456"));
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
