package com.example.mygsm;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Objects;

public class GSMService extends Service {
    private static final String CHANNEL_ID = "gsm_channel_id";

    TelephonyManager tm;
    PhoneStateListener listener;
    List<CellInfo> cellInfos;

    int count = 0;

    // Callback Service → MainActivity
    public interface Callback {
        // voir pour changer les types en fonction de l'usage.
        void onUpdate(String type, String value);

    }
    private Callback callback;
    public void setCallback(Callback cb) {
        callback = cb;
    }

    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        public GSMService getService() {
            return GSMService.this;
        }
    }
    // Service → MainActivity
    // voir pour changer les types en fonction de l'usage.
    private void sendUpdate(String type, String value) {
        if (callback != null) {
            callback.onUpdate(type, value);
        } else {
            updateNotification(value);
        }
    }

    // MainActivity → Service
    private BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String cmd = intent.getStringExtra("cmd");
            if (cmd != null) {
                handleCommand(cmd);
            }
        }
    };

    private void handleCommand(String cmd) {
        Log.d("SERVICE", "Commande reçue : " + cmd);
        if (cmd == null) return;
        switch (cmd) {
            case "test":
                updateNotification("test:" + count);
                sendUpdate("test","test" + count);
                count ++;
                Log.d("SERVICE", " test:" + count);
                break;

            case "refresh":
                refresh();
                break;

            case "auto_refresh":
                auto_refresh();
                break;

            case "stop_refresh":
                stop_refresh();
                break;

            default:
                Log.w("SERVICE", "Commande inconnue : " + cmd);
                break;
        }

        // Exemple : démarrer écoute GSM, changer mode, etc.
    }




    private void UpdateCellID(List<CellInfo> cellInfos_tempo){
        if (cellInfos_tempo != null) {
            StringBuilder liste_return = new StringBuilder();
            Log.d("SERVICE", "size=" + cellInfos_tempo.size());
            if (cellInfos_tempo.isEmpty()) sendUpdate("refresh", "Empty");
            for (CellInfo info : cellInfos_tempo) {
                    if (info instanceof CellInfoLte) {
                        CellInfoLte cell = (CellInfoLte) info;
                        int cellId = cell.getCellIdentity().getCi(); // ID
                        int tac = cell.getCellIdentity().getTac(); // Zone
                        int rssi = cell.getCellSignalStrength().getDbm(); // Puissance du signal en dBm

                        Log.d("SERVICE", "CellInfoLte");
                        liste_return.append("-CellInfoLte: CI=").append(cellId).append(" TAC=").append(tac).append(" RSSI=").append(rssi).append("\n");

                    }
                    if (info instanceof CellInfoGsm) {
                        CellInfoGsm cell = (CellInfoGsm) info;
                        int cellId = cell.getCellIdentity().getCid(); // ID
                        int tac = cell.getCellIdentity().getLac(); // Zone
                        int rssi = cell.getCellSignalStrength().getDbm();
                        liste_return.append("-CellInfoGsm: CI=").append(cellId).append(" TAC=").append(tac).append(" RSSI=").append(rssi).append("\n");
                        Log.d("SERVICE", "CellInfoGsm");
                    }

                    if (info instanceof CellInfoWcdma) {
                        CellInfoWcdma cell = (CellInfoWcdma) info;
                        int cellId = cell.getCellIdentity().getCid(); // ID
                        int tac = cell.getCellIdentity().getLac(); // Zone
                        int rssi = cell.getCellSignalStrength().getDbm();
                        liste_return.append("-CellInfoWcdma: CI=").append(cellId).append(" TAC=").append(tac).append(" RSSI=").append(rssi).append("\n");
                        Log.d("SERVICE", "CellInfoWcdma");
                    }
                    /*
                    if (info instanceof CellInfoNr) { // 5G
                        CellInfoNr cell = (CellInfoNr) info;
                        int cellId = cell.getCellIdentity().getCid(); // ID
                        int tac = cell.getCellIdentity().getLac(); // Zone
                        int rssi = cell.getCellSignalStrength().getDbm();
                        Log.d("SERVICE", "CellInfoNr");
                    }
                    */

            }
            sendUpdate("refresh", liste_return.toString());
        }else{
            sendUpdate("refresh", "null");
        }
    }

    private void refresh(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Il manque une/des Permission(s).");
        }else {
            List<CellInfo> cellInfos_tempo = tm.getAllCellInfo();
            UpdateCellID(cellInfos_tempo);


            // si nouveau
            cellInfos = cellInfos_tempo;
        }
    }

    private void auto_refresh(){
        if (tm != null) {
            tm.listen(listener, PhoneStateListener.LISTEN_CELL_INFO);
        }
    }

    private void stop_refresh(){
        if (tm != null) {
            tm.listen(listener, PhoneStateListener.LISTEN_NONE);
        }
    }



    // Update Notification
    private void updateNotification(String newText) {
        Log.d("SERVICE", "updateNotification : " + newText);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GSM update")
                .setStyle(new NotificationCompat.BigTextStyle() .bigText(newText))
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(1, notification); // même ID que startForeground
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 🔥 1. Création du canal de notification (obligatoire Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GSM lecture",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        // 🔥 2. Notification persistante
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GSM lecture")
                .setContentText("Le service GSM est lu.")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) .build();
        // 🔥 3. Lancer le service en mode Foreground
        startForeground(1, notification);

        // LocalBroadcastManager (pour MainActivity → Service)
        IntentFilter filter = new IntentFilter("com.MyGSM.CMD_TO_SERVICE");
        registerReceiver(commandReceiver, filter);


        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            updateNotification("Il manque une/des Permission(s).");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            listener = new PhoneStateListener() {
                @Override
                public void onCellInfoChanged(List<CellInfo> cellInfo) {

                    if (ActivityCompat.checkSelfPermission(GSMService.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(GSMService.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    super.onCellInfoChanged(cellInfo);
                    if (cellInfo != null && !cellInfo.isEmpty()) {
                        UpdateCellID(cellInfo);
                    }

                }
            };

        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Pour ne pas redémarrer le service si kill
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d("SERVICE", "onTaskRemoved");
        // Stop soi
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.d("SERVICE", "Service détruit");
        // Stop le service
        stop_refresh();
        // Stop le Broadcast
        unregisterReceiver(commandReceiver);
        // Stop la Notif
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return binder;
    }
}