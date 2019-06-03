package com.example.triage_app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.triage.model.Rescuer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

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
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    private Rescuer rescuerData;
    CustomAdapter customAdapter;
    private ArrayList<Endpoint> endpoints = new ArrayList<>();

    private boolean discovering = false;

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Endpoint e = new Endpoint();
                    e.setId(endpointId);
                    e.setName(info.getEndpointName());
                    endpoints.add(e);
                    customAdapter.notifyDataSetChanged();
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    // A previously discovered endpoint has gone away.
                    for (Iterator<Endpoint> iterator = endpoints.iterator(); iterator.hasNext(); ) {
                        Endpoint e = iterator.next();
                        if (endpointId.equals(e.getId())) {
                            iterator.remove();
                        }
                    }
                    customAdapter.notifyDataSetChanged();
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
            Nearby.getConnectionsClient(getApplicationContext()).acceptConnection(endpointId, payloadReciever);
            //Toast.makeText(getApplicationContext(), "Połączenie ustanowione z " + endID, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    // We're connected! Can now start sending and receiving data.
                    //Toast.makeText(getApplicationContext(), "Połączenie ustanowione z " + endpointId, Toast.LENGTH_SHORT).show();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try {
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(rescuerData);
                        oos.flush();
                        byte[] data = bos.toByteArray();
                        Payload bytesPayload = Payload.fromBytes(data);
                        Nearby.getConnectionsClient(getApplicationContext()).sendPayload(endpointId, bytesPayload)
                                .addOnSuccessListener(
                                        (Void unused) -> {
                                            Nearby.getConnectionsClient(getApplicationContext()).disconnectFromEndpoint(endpointId);
                                        });
                        //Toast.makeText(getApplicationContext(), "Wysłano", Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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
                            Toast.makeText(getApplicationContext(), "Skanowanie w poszukiwaniu czujników", Toast.LENGTH_SHORT).show();
                            discovering = true;
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            Toast.makeText(getApplicationContext(), "Błąd inicjalizacji nadajnika, \nnadajnik jest wykorzystywany przez inną aplikację", Toast.LENGTH_SHORT).show();
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


        if (isLoggedIn()) {
            Toast.makeText(MainActivity.this, "Zalogowany", Toast.LENGTH_SHORT);
            Log.e("Login", "Zalogowany");
        } else {
            AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);
            View mView = getLayoutInflater().inflate(R.layout.dialog_login, null);
            final EditText mId = (EditText) mView.findViewById(R.id.etId);
            final EditText mPassword = (EditText) mView.findViewById(R.id.etPassword);
            Button mLogin = (Button) mView.findViewById(R.id.login_button);


            mBuilder.setView(mView);
            mBuilder.setCancelable(false);
            final AlertDialog dialog = mBuilder.create();
            dialog.show();


            File path = getApplicationContext().getFilesDir();
            File file = new File(path, "last_login.txt");
            Log.e("Path", path.getPath());

            //sprawdzenie czy login pasuje do schematu - R00 - R99
            Pattern compiledPattern = Pattern.compile("R[0-9][0-9]");
            Matcher matcher = compiledPattern.matcher(mId.getText().toString());

            mLogin.setOnClickListener(v -> {
                if (!mId.getText().toString().isEmpty() && !mPassword.getText().toString().isEmpty() &&
                        matcher.matches()) {
                    Toast.makeText(MainActivity.this,
                            "Zalogowano",
                            Toast.LENGTH_SHORT).show();

                    //zapis do pliku login, hasło, czas w sekundach
                    try {
                        FileOutputStream stream = new FileOutputStream(file);
                        Date date = new Date();
                        String login = mId.getText().toString() + "\n" +
                                mPassword.getText().toString() + "\n" +
                                date.getTime() + "\n";
                        Log.e("Zapis", login);
                        stream.write(login.getBytes());
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    dialog.dismiss();

                } else {
                    Toast.makeText(MainActivity.this,
                            "Wypełnij pola",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    protected String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }


    //sprawdzenie czy uzytkownik nie był wczesniej zalogowany
    //chyba nie do konca dziala, nie wiadomo dlaczego
    private boolean isLoggedIn() {
        File path = getApplicationContext().getExternalFilesDir(null);
        File file = new File(path, "last_login.txt");
        int length = (int) file.length();

        byte[] bytes = new byte[length];


        if (file.exists()) {
            try {
                FileInputStream in = new FileInputStream(file);
                in.read(bytes);
                in.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String contents = new String(bytes);
            Log.e("isLoggedIn", contents);

            return true;
        } else {
            Log.e("isLoggedIn", "brak pliku");

            return false;
        }


    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main_activity);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        startDiscovery();

        ListView lv = findViewById(R.id.endpoint_list);
        customAdapter = new CustomAdapter(this, endpoints);
        lv.setAdapter(customAdapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Endpoint end = endpoints.get(position);
            Nearby.getConnectionsClient(getApplicationContext()).requestConnection(end.getName(), end.getId(), connectionLifecycleCallback)
                    .addOnSuccessListener(
                            (Void unused) -> {
                                Toast.makeText(getApplicationContext(), "Połączono z " + end.getName(), Toast.LENGTH_SHORT).show();
                            })
                    .addOnFailureListener(
                            (Exception e) -> {
                                Toast.makeText(getApplicationContext(), "Błąd połączenia z " + end.getName(), Toast.LENGTH_SHORT).show();
                            });
        });

        TextView t = findViewById(R.id.reset_transmitter);
        t.setOnClickListener(v -> {
            if (discovering) {
                Nearby.getConnectionsClient(getApplicationContext()).stopDiscovery();
            }
            startDiscovery();
            updateSettings();
        });

        t = findViewById(R.id.clear_endpoints);
        t.setOnClickListener(v -> {
            Nearby.getConnectionsClient(getApplicationContext()).stopAllEndpoints();
            endpoints.clear();
            customAdapter.notifyDataSetChanged();
        });

    }

    public void updateSettings() {
        TextView t = findViewById(R.id.transmitter_status_label);
        t.setText(discovering ? "szuka czujników" : "bezczynny");
    }

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
        switch (id) {
            case R.id.action_settings:
                updateSettings();
                vf.setDisplayedChild(1);
                return true;
            case R.id.action_attributes:
                vf.setDisplayedChild(2);
                return true;
            default:
                vf.setDisplayedChild(0);
        }

        return super.onOptionsItemSelected(item);
    }
}
