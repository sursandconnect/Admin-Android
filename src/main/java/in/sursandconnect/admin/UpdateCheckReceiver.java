package in.sursandconnect.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Legacy background bulk-update receiver.
 * Disabled because the Admin app now refreshes individual modules on demand.
 */
public class UpdateCheckReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Intentionally disabled.
    }
}
