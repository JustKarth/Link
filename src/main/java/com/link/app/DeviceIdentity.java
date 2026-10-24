
package com.link.app;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class DeviceIdentity {

    private static final String KEY = "localPeerId";

    private DeviceIdentity() {
    }

    public static String loadOrCreate() {
        Preferences preferences =
                Preferences.userNodeForPackage(DeviceIdentity.class);

        String id = preferences.get(KEY, null);

        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            preferences.put(KEY, id);

            try {
                preferences.flush();
            } catch (BackingStoreException e) {
                throw new IllegalStateException(
                        "Could not persist device identity", e
                );
            }
        }

        return id;
    }
}
