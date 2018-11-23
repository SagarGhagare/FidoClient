package org.ebayopensource.fidouafclient;

import android.util.Log;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import org.ebayopensource.fidouafclient.util.Preferences;

public class notificationTokenManager extends FirebaseInstanceIdService {
    private static final String TAG = notificationTokenManager.class.getSimpleName();
    public static String notificationToken = Preferences.getSettingsParam("notificationToken");
    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + refreshedToken);
        notificationTokenManager.notificationToken = refreshedToken;
        Preferences.setSettingsParam("notificationToken", notificationToken);
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        //sendRegistrationToServer(refreshedToken);
    }
}
