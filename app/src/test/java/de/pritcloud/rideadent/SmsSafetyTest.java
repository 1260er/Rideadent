package de.pritcloud.rideadent;

import org.junit.Test;
import static org.junit.Assert.*;

public class SmsSafetyTest {
    @Test public void acceptsNormalNumbersAndNormalizesInternationalPrefix() {
        assertEquals("+493012345678", SmsSafety.destination("0049 30 12345678"));
        assertEquals("+493012345678", SmsSafety.destination("+49 (30) 12345678"));
        assertEquals("03012345678", SmsSafety.destination("030 12345678"));
        assertEquals("017612345678", SmsSafety.destination("0176 12345678"));
        assertEquals("+441234567890", SmsSafety.destination("+44 1234 567890"));
    }

    @Test public void rejectsShortInvalidAndKnownGermanSpecialNumbers() {
        for (String number : new String[] {
                null, "", "112", "110", "911", "123456", "*123#",
                "sip:test@example.org", "09001234567", "01901234567",
                "01371234567", "01801234567", "1181234567",
                "+499001234567", "+491371234567", "00491801234567",
                "+490", "+441234567890123456789"}) {
            assertNull(number, SmsSafety.destination(number));
        }
    }

}
