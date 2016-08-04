package android.text;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextUtilsTest {
    @Test
    public void testIsEmpty() throws Exception {
        assertTrue(TextUtils.isEmpty(null));
        assertTrue(TextUtils.isEmpty(""));
        assertFalse(TextUtils.isEmpty("empty (heh)"));
    }

    @Test
    public void testIsDigitsOnly() throws Exception {
        assertTrue(TextUtils.isDigitsOnly(""));
        assertTrue(TextUtils.isDigitsOnly("1289043"));
        assertFalse(TextUtils.isDigitsOnly("1289043z"));
    }

    @Test
    public void testEquals() throws Exception {
        assertTrue(TextUtils.equals(null, null));
        assertFalse(TextUtils.equals(null, "a"));
        assertFalse(TextUtils.equals("a", null));
        assertTrue(TextUtils.equals("a", "a"));
        assertFalse(TextUtils.equals("a", "b"));
        assertFalse(TextUtils.equals("a", "ab"));
    }
}