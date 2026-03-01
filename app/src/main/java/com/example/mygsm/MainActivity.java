package com.example.mygsm;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mygsm.databinding.ActivityMainBinding;


public class MainActivity extends AppCompatActivity  implements GSMService.Callback {

    private ActivityMainBinding binding;

    private boolean isBound = false;
    private GSMService myService = null;


    // Service → MainActivity
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            GSMService.LocalBinder b = (GSMService.LocalBinder) binder;
            myService = b.getService();
            myService.setCallback(MainActivity.this);
            isBound = true;
            Log.d("APP", "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            Log.d("APP", "onServiceDisconnected");
        }
    };
    // Callback  service => MainActivity
    @Override
    public void onUpdate(String type, String value) {
        runOnUiThread(() -> {
            // Mise à jour UI
            Log.d("APP", type + " = " + value);

            if (type == null) return;
            switch (type) {
                case "test":
                    binding.info.setText(type + " = " + value);
                    break;

                case "refresh":
                    binding.data.setText(value);
                    break;

                default:
                    Log.w("APP", "Commande inconnue : " + type);
                    break;
            }


            // reagire si le service est kill par le tasklist
        });
    }

    // MainActivity → Service
    private void sendCommandToService(String cmd) {
        Intent intent = new Intent("com.MyGSM.CMD_TO_SERVICE");
        intent.putExtra("cmd", cmd);
        sendBroadcast(intent);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Lire getSharedPreferences pour ajouter les paramètres précedent.
        /*
        SharedPreferences prefs = getSharedPreferences("my_prefs", MODE_PRIVATE);
        String savedText = prefs.getString("saved_text", "");
         */
        binding.actif.setChecked(false);
        binding.actif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (binding.actif.isChecked()) {
                    startMyService();
                }else{
                    StopMyService();
                    binding.actif2.setChecked(false);
                }
            }
        });
        binding.actif2.setChecked(false);
        binding.actif2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AutoRefreshMyService(binding.actif.isChecked());
            }
        });

        binding.refresh.setOnClickListener(v ->
                RefreshMyService()
        );

    }

    @Override
    public void onResume() {
        super.onResume();
        // ajouter les permissions
        demanderPermissions();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (myService != null){
            if (!isBound) {
                Intent intent = new Intent(this, GSMService.class);
                bindService(intent, connection, Context.BIND_AUTO_CREATE);
            }
            RefreshMyService();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        // Ajouter des parametre dans getSharedPreferences
        /*
        String text = binding.editTextText.getText().toString();
        SharedPreferences prefs = requireContext().getSharedPreferences("my_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("saved_text", text);
        editor.apply(); // async
        */

        if (isBound) {
            myService.setCallback(null);
            unbindService(connection);
            isBound = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        StopMyService();
        Log.i("APP", "onDestroy");
    }


    private void startMyService(){
        Log.i("APP", "startMyService");

        Intent intent = new Intent(this, GSMService.class);
        ContextCompat.startForegroundService(this,intent);

        // Android 13 : permission pour notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{ Manifest.permission.POST_NOTIFICATIONS },
                    1002);
        }

        if (!isBound) {
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }
    }

    private void StopMyService(){
        Log.i("APP", "StopMyService");
        if (isBound) {
            myService.setCallback(null);
            unbindService(connection);
            isBound = false;
        }
        AutoRefreshMyService(false);
        Intent intent = new Intent(this, GSMService.class);
        stopService(intent);
        myService = null;
    }

    private void ActionMyService(){
        sendCommandToService("test");
    }

    private void RefreshMyService(){
        sendCommandToService("refresh");
    }

    private void AutoRefreshMyService(Boolean actif){
        if (actif = true) {
            sendCommandToService("auto_refresh");
        } else {
            sendCommandToService("stop_refresh");
        }

    }

    // Ajouter les Permissions général.
    private void demanderPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                    },
                    1000);
        }
    }
}