package android.os;

import java.util.HashMap;
import java.util.Map;

/**
 * Fake implementation of Bundle for local tests (so we don't have to do tricks to pull in actual
 * android implementations)
 *
 * For more about why this is needed, see
 * http://tools.android.com/tech-docs/unit-testing-support#TOC-Method-...-not-mocked.-
 */
public class Bundle {
    private Map<String, String> mValues = new HashMap<>();

    public void putDouble(String key, double value) {
        mValues.put(key, String.valueOf(value));
    }

    public double getDouble(String key) {
        return Double.parseDouble(mValues.get(key));
    }
}
