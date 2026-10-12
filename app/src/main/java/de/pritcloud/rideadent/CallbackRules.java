package de.pritcloud.rideadent;

/** Only conventional numeric telephone numbers may be called automatically. */
final class CallbackRules {
    private CallbackRules() {}

    // Compare only validated numbers; punctuation and international 00/+ formats
    // must not create separate callback entries. No country-code guessing.
    // Used only to identify the same validated number in callback records.
    // Do not guess country codes or modify the number used for actual dialing.
    static String identity(String value) {
        String number = dialNumber(value);
        if (number == null) return null;
        return number.startsWith("00") ? "+" + number.substring(2) : number;
    }

    static boolean sameNumber(String first, String second) {
        String a = identity(first);
        String b = identity(second);
        return a != null && a.equals(b);
    }

    static String dialNumber(String value) {
        if (value == null || value.isBlank() || value.length() > 48) return null;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
            else if (c == '+' && i == 0) digits.append(c);
            else if (c != ' ' && c != '-' && c != '.' && c != '(' && c != ')') return null;
        }
        int count = digits.length() - (digits.length() > 0 && digits.charAt(0) == '+' ? 1 : 0);
        if (count < 3 || count > 20) return null;
        String number = digits.toString();
        // Android ACTION_CALL is not permitted to place emergency calls.
        if (number.equals("112") || number.equals("110") || number.equals("911")
                || number.equals("999") || number.equals("000")) return null;
        return number;
    }
}
