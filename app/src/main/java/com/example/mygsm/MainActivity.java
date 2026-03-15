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
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import com.example.mygsm.database.AppDatabase;
import com.example.mygsm.database.Data;

import com.example.mygsm.databinding.ActivityMainBinding;

import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity  implements GSMService.Callback {

    private ActivityMainBinding binding;

    private boolean isBound = false;
    private GSMService myService = null;

    private AppDatabase db;

    // Liste des Data
    List<Data> datalist;
    DataAdapter adapter;

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
            Log.d("APP", "onUpdate:" + type + " = " + value);

            if (type == null) return;
            switch (type) {
                case "test":
                    binding.data.setText(type + " = " + value);
                    Toast.makeText(MainActivity.this,
                            "test: ",
                            Toast.LENGTH_SHORT).show();
                    break;

                case "refresh":
                    binding.data.setText(value);
                    if ( !value.equals("Empty") && !value.isEmpty()) {
                        loadDataInFront();
                    }
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

    private void updateDataFront() {
        new Thread(() -> {
            if (!binding.Cellid.getText().toString().isEmpty()) {
                Data existing = db.dataDao().getByCi(Integer.parseInt(binding.Cellid.getText().toString()));
                if (existing != null) {
                    existing.name = binding.name.getText().toString();
                    existing.info = binding.infos.getText().toString();
                    existing.alert = binding.alerte.isChecked();
                    db.dataDao().update(existing);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "UPDATE : " + binding.Cellid.getText().toString(), Toast.LENGTH_SHORT).show();
                        loadDataInFront();
                    });
                    //alert_refresh (pour actualise la list des alert dans le service
                    sendCommandToService("alert_refresh");
                }else{
                    Toast.makeText(MainActivity.this, "UPDATE? : " + Integer.parseInt(binding.Cellid.getText().toString()) + ";" + binding.Cellid.getText().toString(), Toast.LENGTH_SHORT).show();
                }
            }
        }).start();
    }
    private void loadDataInFront() {
        new Thread(() -> {
            if (binding.alerte.isChecked()) {
                datalist = db.dataDao().getAlertData();
            }else{
                datalist = db.dataDao().getNotAlertData();
            }
            Log.d("APP", "data: " + datalist.size());
            if (datalist != null) {
                if (!datalist.isEmpty()){
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                "getAllData: " + datalist.size(),
                                Toast.LENGTH_SHORT).show();
                        adapter.updateData(datalist);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(MainActivity.this,
                                "getAllData: show " + datalist.size(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
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
                AutoRefreshMyService(binding.actif2.isChecked());
            }
        });

        binding.alerte.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadDataInFront();
            }
        });

        binding.refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RefreshMyService();
            }
        });

        binding.reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.data.setText("");
                binding.Cellid.setText("");
                binding.infos.setText("");
                binding.date.setText("");
                binding.count.setText("");
            }
        });

        binding.update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateDataFront();
            }
        });

        binding.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActionMyService();
            }
        });


        db = Room.databaseBuilder( getApplicationContext(), AppDatabase.class, "my_database" ).build(); // pas de allowMainThreadQueries()

        RecyclerView recyclerView = findViewById(R.id.liste);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new DataAdapter(datalist, (item, position) -> {
            // Quand on clique sur un item
            //Toast.makeText(this, "Sélection : " + item.Ci, Toast.LENGTH_SHORT).show();
            Data d = datalist.get(position);
            binding.Cellid.setText(String.valueOf(d.Ci));
            binding.name.setText(d.name);
            binding.infos.setText(d.info);
            binding.count.setText(String.valueOf(d.count));
            binding.alerte.setChecked(d.alert);
            binding.date.setText(new Date(d.date).toString());
            Log.d("APP", "DataAdapter LISTE");
        });

        recyclerView.setAdapter(adapter);

        loadDataInFront();
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
        if (actif == true) {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED ){
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.VIBRATE,
                    },
                    1001);
        }

    }
}