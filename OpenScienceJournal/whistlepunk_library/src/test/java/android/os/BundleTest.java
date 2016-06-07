package android.os;


import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BundleTest {
    @Test
    public void putDouble() {
        Bundle bundle = new Bundle();
        bundle.putDouble("key", 5);
        assertEquals(5, bundle.getDouble("key"), 0.1);
    }
}
