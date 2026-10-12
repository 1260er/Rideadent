package de.pritcloud.rideadent;

/** Conservative validation for automatic SMS destinations. */
final class SmsSafety {
    private SmsSafety() {}

    /** Return a sendable destination and stable 00/+ key, or null. */
    static String destination(String raw) {
        String number = CallbackRules.dialNumber(raw);
        if (number == null) return null;
        if (number.startsWith("00")) number = "+" + number.substring(2);
        int digits = number.length() - (number.startsWith("+") ? 1 : 0);
        // Refuse short codes and implausibly long international numbers.
        if (digits < 7 || digits > 15) return null;
        String german = number.startsWith("+49")
                ? "0" + number.substring(3) : number;
        // Known German premium/service prefixes must never receive auto-replies.
        // These are not exhaustive protection against chargeable international SMS.
        if (german.startsWith("0900") || german.startsWith("0190")
                || german.startsWith("0137") || german.startsWith("0180")
                || german.startsWith("118")) return null;
        return number;
    }

}
