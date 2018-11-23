package org.ebayopensource.fidouafclient;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.widget.Toast;

public class MyBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent startServiceIntent = new Intent(context, notificationListenerService.class);
        context.startService(startServiceIntent);
        Intent startServiceIntent2 = new Intent(context, notificationTokenManager.class);
        context.startService(startServiceIntent2);
        Toast.makeText(context, "Got Startup Broadcast",
                Toast.LENGTH_LONG).show();

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_fp_40px)
                .setContentTitle("NHS App")
                .setContentText("The NHS App has started and is ready to accept authentication notifications")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

// notificationId is a unique int for each notification that you must define
        notificationManager.notify(0, mBuilder.build());
    }

}
