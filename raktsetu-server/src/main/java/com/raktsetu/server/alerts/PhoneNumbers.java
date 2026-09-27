package com.raktsetu.server.alerts;

import java.util.regex.Pattern;

/** Mobile numbers in international (E.164) form, as SMS and WhatsApp providers require. */
public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");
    private static final Pattern INDIAN_MOBILE = Pattern.compile("[6-9]\\d{9}");

    private PhoneNumbers() {
    }

    /**
     * Accepts {@code +919812345678}, {@code 9812345678}, {@code 09812345678} or {@code 91 98123 45678} (spaces,
     * dashes and brackets are ignored) and returns {@code +919812345678}. Ten-digit numbers are taken as Indian.
     *
     * @throws IllegalArgumentException if it is not a mobile number
     */
    public static String normalise(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Enter a mobile number");
        }
        String digits = input.replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        }
        if (!digits.startsWith("+")) {
            if (digits.startsWith("0")) {
                digits = digits.substring(1);
            }
            if (digits.length() == 12 && digits.startsWith("91")) {
                digits = digits.substring(2);
            }
            if (!INDIAN_MOBILE.matcher(digits).matches()) {
                throw new IllegalArgumentException("Enter a 10-digit mobile number, or an international one "
                        + "starting with +");
            }
            digits = "+91" + digits;
        }
        if (!E164.matcher(digits).matches()) {
            throw new IllegalArgumentException("That does not look like a mobile number");
        }
        return digits;
    }

    /** For logs and screens that should not show the whole number: {@code +91******5678}. */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 7) + phone.substring(phone.length() - 4);
    }
}
