package org.ebayopensource.fidouafclient;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class notificationListenerService extends FirebaseMessagingService {

    private static final String TAG = notificationListenerService.class.getSimpleName();
    public static final String AUTHENTICATION_EXTRA = "AUTHENTICATION_SESSION";

    public notificationListenerService() {
    }
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // ...

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());

            if (/* Check if data needs to be processed by long running job */ true) {
                // For long-running tasks (10 seconds or more) use Firebase Job Dispatcher.
                scheduleJob(remoteMessage);
            } else {
                // Handle message within 10 seconds
                handleNow(remoteMessage);
            }

        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            //scheduleJob();
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }

    private void scheduleJob(RemoteMessage remoteMessage){
        Log.d(TAG, "About to start MainActivity");
        Intent dialogIntent = new Intent(this, MainActivity.class);
        dialogIntent.setAction(Intent.ACTION_VIEW);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        dialogIntent.putExtra(notificationListenerService.AUTHENTICATION_EXTRA, (String) remoteMessage.getData().get(notificationListenerService.AUTHENTICATION_EXTRA));
        startActivity(dialogIntent);
    }
    private void handleNow(RemoteMessage remoteMessage){}
}
