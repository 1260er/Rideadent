package de.pritcloud.rideadent;

/** Only conventional numeric telephone numbers may be called automatically. */
final class CallbackRules {
    private CallbackRules() {}

    // Compare only validated numbers; punctuation and international 00/+ formats
    // must not create separate callback entries. No country-code guessing.
    static boolean sameNumber(String first, String second) {
        String a = dialNumber(first);
        String b = dialNumber(second);
        if (a == null || b == null) return false;
        if (a.startsWith("00")) a = "+" + a.substring(2);
        if (b.startsWith("00")) b = "+" + b.substring(2);
        return a.equals(b);
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
