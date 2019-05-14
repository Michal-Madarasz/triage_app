package com.example.triage_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Application;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.triage.model.Rescuer;
import com.triage.model.Victim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE
            };

    private String SERVICE_ID = "triage.rescuer-simulator";
    //private String endID = "";
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    private Rescuer rescuerData;
    CustomAdapter customAdapter;
    private ArrayList<Endpoint> endpoints = new ArrayList<>();

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    // An endpoint was found. We request a connection to it.
                    Endpoint e = new Endpoint();
                    e.setId(endpointId);
                    e.setName(info.getEndpointName());
                    endpoints.add(e);
                    customAdapter.notifyDataSetChanged();
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    // A previously discovered endpoint has gone away.
                }
            };


    private PayloadCallback payloadReciever = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String s, Payload payload) {

        }

        @Override
        public void onPayloadTransferUpdate(String s, PayloadTransferUpdate payloadTransferUpdate) {

        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Autoryzacja")
                    .setMessage("Wykryto próbę połączenia.\nCzy drugie urządzenie wyświetla kod: " + connectionInfo.getAuthenticationToken())
                    .setPositiveButton("tak", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Nearby.getConnectionsClient(getApplicationContext()).acceptConnection(endpointId, payloadReciever);
                        }
                    })
                    .setNegativeButton("nie", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Nearby.getConnectionsClient(getApplicationContext()).rejectConnection(endpointId);
                        }
                    })
                    .show();
            //Toast.makeText(getApplicationContext(), "Połączenie ustanowione z " + endID, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    // We're connected! Can now start sending and receiving data.
                    Toast.makeText(getApplicationContext(), "Połączenie ustanowione z " + endpointId, Toast.LENGTH_SHORT).show();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try {
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(rescuerData);
                        oos.flush();
                        byte[] data = bos.toByteArray();
                        Payload bytesPayload = Payload.fromBytes(data);
                        Nearby.getConnectionsClient(getApplicationContext()).sendPayload(endpointId, bytesPayload);
                        //Toast.makeText(getApplicationContext(), "Wysłano", Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Nearby.getConnectionsClient(getApplicationContext()).disconnectFromEndpoint(endpointId);
                    break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    // The connection was rejected by one or both sides.
                    break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    // The connection broke before it was able to be accepted.
                    break;
                default:
                    // Unknown status code
            }
        }

        @Override
        public void onDisconnected(String endpointId) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.

        }
    };

    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startDiscovery() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build();
        Nearby.getConnectionsClient(getApplicationContext()).startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            Toast.makeText(getApplicationContext(), "Startujemy odkrywanie", Toast.LENGTH_SHORT).show();
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            Toast.makeText(getApplicationContext(), "Nie wystartowano odkrywania", Toast.LENGTH_SHORT).show();
                            Log.e("TAG", e.getMessage());
                        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasPermissions(this, getRequiredPermissions())) {
            if (!hasPermissions(this, getRequiredPermissions())) {
                if (Build.VERSION.SDK_INT < 23) {
                    ActivityCompat.requestPermissions(
                            this, getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
                } else {
                    requestPermissions(getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT < 23) {
                ActivityCompat.requestPermissions(
                        this, getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
            } else {
                requestPermissions(getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
            }
            return;
        }
        TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
        Rescuer r = new Rescuer();

        r.setId(tm.getDeviceId());
        final EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Podaj swoje dane")
                .setMessage("Podaj swoje imię i nazwisko w celu identyfikacji (pole może być puste)")
                .setView(name)
                .setPositiveButton("Kontynuuj", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        r.setName(name.getText().toString());
                        rescuerData = r;
                    }
                })
                .setNegativeButton("Wyjdź", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(1);
                    }
                })
                .show();
    }

    protected String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        startDiscovery();

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);


        ListView lv = findViewById(R.id.endpoint_list);
        customAdapter = new CustomAdapter(this, endpoints);
        lv.setAdapter(customAdapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Endpoint end = endpoints.get(position);
                Nearby.getConnectionsClient(getApplicationContext()).requestConnection(end.getName(), end.getId(), connectionLifecycleCallback)
                        .addOnSuccessListener(
                                (Void unused) -> {
                                    Toast.makeText(getApplicationContext(), "Połączenie uzyskane", Toast.LENGTH_SHORT).show();
                                })
                        .addOnFailureListener(
                                (Exception e) -> {
                                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
            }
        });


        Button sendButton = findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                Random r = new Random();
                long imei = r.nextLong() % 1000000000000000L;
                boolean b = r.nextBoolean();
                int rate = r.nextInt(40) + 10;
                float capRefillTime = r.nextFloat() * 3f + 0.5f;
                boolean w = r.nextBoolean();
                Victim.AVPU c = Victim.AVPU.values()[r.nextInt(Victim.AVPU.values().length)];
                Victim randomVictim = new Victim(imei, b, rate, capRefillTime, w, c);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(randomVictim);
                    oos.flush();
                    byte[] data = bos.toByteArray();
                    Payload bytesPayload = Payload.fromBytes(data);
                    Nearby.getConnectionsClient(getApplicationContext()).sendPayload(endID, bytesPayload);
                    Toast.makeText(getApplicationContext(), "Wysłano", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                */
            }
        });

    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        ViewFlipper vf = findViewById(R.id.layout_manager);
        switch(id){
            case R.id.action_settings:
                vf.setDisplayedChild(1);
                return true;
            default:
                vf.setDisplayedChild(0);
        }

        return super.onOptionsItemSelected(item);
    }
    */


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        ViewFlipper flipper = findViewById(R.id.layout_manager);

        switch (id) {
            case R.id.nav_coordinator:
                flipper.setDisplayedChild(0);
                return true;
            case R.id.nav_victim:
                flipper.setDisplayedChild(2);
                return true;
            case R.id.nav_status:
                flipper.setDisplayedChild(0);
                return true;
            case R.id.nav_settings:
//                flipper.setDisplayedChild(1);
                return true;
            default:
                flipper.setDisplayedChild(0);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
