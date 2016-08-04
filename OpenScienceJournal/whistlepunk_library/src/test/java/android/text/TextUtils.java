package android.text;

import android.os.Bundle;

import java.util.Objects;

/**
 * Fake implementation of TextUtils for local tests.
 *
 * For details, see {@link Bundle} in this folder.
 */
public class TextUtils {
    public static boolean isEmpty(CharSequence string) {
        return string == null || string.length() == 0;
    }

    public static boolean isDigitsOnly(CharSequence string) {
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        return Objects.equals(a, b);
    }
}
