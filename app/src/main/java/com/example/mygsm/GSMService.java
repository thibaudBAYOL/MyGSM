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
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.room.Room;
import com.example.mygsm.database.AppDatabase;
import com.example.mygsm.database.Data;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;



public class GSMService extends Service {
    private static final String CHANNEL_ID = "gsm_channel_id";

    TelephonyManager tm;

    PhoneStateListener phoneStateListener;

    long lastCallBack = 0;
    TelephonyCallback telephonyCallback;
    private WakeLock wakeLock;

    List<Integer> lastData = new ArrayList<>();

    private AppDatabase db;

    List<Data> dataAlert;
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
    private boolean isRunning = false;
    private boolean isRunningHard = false;
    private final Handler handler = new Handler();
    private final Runnable autoRefreshTask = new Runnable() {
        @Override
        public void run() {

            if( (System.currentTimeMillis() - lastCallBack) > 30000){
                // si > 30s sans refresh du callback alors refresh tout les 6s
                refresh();
                if (!isRunningHard){
                    showNotification("refresh hard");
                    isRunningHard = true;
                }
                handler.postDelayed(this, 6000);
            }else{
                if (isRunningHard){
                    showNotification("refresh CallBack");
                    isRunningHard = false;
                }
                // verifier tout les 10s
                handler.postDelayed(this, 10000);
            }
        }
    };

    // Service → MainActivity
    // voir pour changer les types en fonction de l'usage.
    private void sendUpdate(String type, String value) {
        if (callback != null) {
            callback.onUpdate(type, value);
        } else {
            updateNotification("count=" + count + "\n" + type + ":" + value);
            count ++;
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
                showNotification("test" + count);
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

            case "alert_refresh":
                loadDataInBackground();
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
            if (cellInfos_tempo.isEmpty()) {
                sendUpdate("refresh", "Empty");
                return;
            }
            List<Integer> last_tempo = new ArrayList<>();
            boolean alert_existe = false;
            for (CellInfo info : cellInfos_tempo) {
                int cellId = 0;  // ID
                int tac = 0;
                int rssi = 0;
                String type = "";
                if (info instanceof CellInfoLte) {

                        CellInfoLte cell = (CellInfoLte) info;
                        cellId = cell.getCellIdentity().getCi(); // ID
                        tac = cell.getCellIdentity().getTac(); // Zone
                        rssi = cell.getCellSignalStrength().getDbm(); // Puissance du signal en dBm

                        Log.d("SERVICE", "CellInfoLte");
                        type = "CellInfoLte";
                    } else if (info instanceof CellInfoGsm) {
                        CellInfoGsm cell = (CellInfoGsm) info;
                        cellId = cell.getCellIdentity().getCid(); // ID
                        tac = cell.getCellIdentity().getLac(); // Zone
                        rssi = cell.getCellSignalStrength().getDbm();
                        type = "CellInfoGsm";
                        Log.d("SERVICE", "CellInfoGsm");
                    } else if (info instanceof CellInfoWcdma) {
                        CellInfoWcdma cell = (CellInfoWcdma) info;
                        cellId = cell.getCellIdentity().getCid(); // ID
                        tac = cell.getCellIdentity().getLac(); // Zone
                        rssi = cell.getCellSignalStrength().getDbm();
                        Log.d("SERVICE", "CellInfoWcdma");
                        type = "CellInfoWcdma";
                    } else if (info instanceof CellInfoNr) { // 5G
                    CellInfoNr cell = (CellInfoNr) info;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            // Récupération dynamique (réflexion)
                            Method mNci = cell.getCellIdentity().getClass().getMethod("getNci");
                            Method mTac = cell.getCellIdentity().getClass().getMethod("getTac");

                            cellId = (int) mNci.invoke(cell.getCellIdentity());
                            tac = (int) mTac.invoke(cell.getCellIdentity());

                        } catch (Exception e) {
                            Log.w("NR", "Méthodes NR non disponibles");
                        }
                    }
                    rssi = cell.getCellSignalStrength().getDbm();
                    Log.d("SERVICE", "CellInfoNr");
                    type = "CellInfoNr";
                }
                liste_return.append(type).append(": CI=").append(cellId).append(" TAC=").append(tac).append(" RSSI=").append(rssi);
                if (info.isRegistered()) liste_return.append("SERVING");

                // Only if cellId existe
                if ( cellId > 1000 && cellId != 65535 /*&& tac > 100 && tac != 65535*/) {
                    // lastData vs cellId // si n'est pas déjà passé
                    if (!lastData.contains(cellId)) {
                        if (dataAlert != null){
                            // verify if alert dataAlert
                            for (Data d : dataAlert) {
                                if (d.Ci == cellId) {
                                    alert_existe = true;
                                    showNotification("Alert: " + cellId + " "+ d.name);
                                    //break;
                                }
                            }
                        }
                        insertDataInBackground(cellId);
                    }
                    last_tempo.add(cellId);
                }
                liste_return.append("\n");
            }
            if (alert_existe){
                // fair un buzz
                vibrateOnce();
            }

            // recupere les cellId (last_tempo) et les ajouter dans lastData
            lastData.clear();
            lastData.addAll(last_tempo);

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
        }
    }

    private void auto_refresh() {
        if (!isRunning) {
            lastCallBack = System.currentTimeMillis();
            handler.post(autoRefreshTask);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.registerTelephonyCallback(getMainExecutor(), telephonyCallback);
            }else{
                tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO);
            }

            // Active le WakeLock
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
                Log.d("SERVICE", "WakeLock ACQUIRED");
            }

            Log.d("SERVICE", "auto_refresh");
            isRunning = true;
        }
    }

    private void stop_refresh() {
        if (isRunning) {
            handler.removeCallbacks(autoRefreshTask);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (telephonyCallback != null) {
                    tm.unregisterTelephonyCallback(telephonyCallback);
                }
            }else{
                if (phoneStateListener != null) {
                    tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                }
            }

            // Libère le WakeLock
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d("SERVICE", "WakeLock RELEASED (stop_refresh)");
            }

            Log.d("SERVICE", "stop_refresh isRunning");
            isRunning = false;
        }
    }



    // Update Notification
    private void updateNotification(String newText) {
        Log.d("SERVICE", "updateNotification : " + newText);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GSM lecture")
                .setStyle(new NotificationCompat.BigTextStyle() .bigText(newText))
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(1, notification); // même ID que startForeground
    }
    private void showNotification(String message) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_compass)   // ton icône
                .setContentTitle("MyGSM notif")
                .setStyle(new NotificationCompat.BigTextStyle() .bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        int uniqueId = (int) System.currentTimeMillis();
        manager.notify(uniqueId, builder.build());
    }

    private void insertDataInBackground(int Ci) {
        new Thread(() -> {
            Data existing = db.dataDao().getByCi(Ci);
            long now = System.currentTimeMillis();
            if (existing != null) {
                existing.count ++;
                existing.date = now;
                db.dataDao().update(existing);
            } else {
                db.dataDao().insert(new Data("New","" , Ci, false,1,now));
            }
        }).start();
    }

    private void loadDataInBackground() {
        new Thread(() -> {
            dataAlert = db.dataDao().getAlertData();
            Log.d("SERVICE", "dataAlert: " + dataAlert.size());
        }).start();
    }

    private void vibrateOnce() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                long[] pattern = {0, 100, 50, 200};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    v.vibrate(pattern, -1);
                }
            } else {
                v.vibrate(200);
            }
        }
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
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Il manque une/des Permission(s).");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            telephonyCallback = new TelephonyCallback() {
                public void onCellInfoChanged(List<CellInfo> cellInfo) {
                    UpdateCellID(cellInfo);
                    lastCallBack = System.currentTimeMillis();
                }
            };

        } else {
            // Android 10–11
            phoneStateListener = new PhoneStateListener() {
                @Override
                public void onCellInfoChanged(List<CellInfo> cellInfo) {
                    UpdateCellID(cellInfo);
                    lastCallBack = System.currentTimeMillis();
                }
            };

        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyGSM:WakeLock");

        db = Room.databaseBuilder( getApplicationContext(), AppDatabase.class, "my_database" ).build(); // pas de allowMainThreadQueries()

        loadDataInBackground();
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