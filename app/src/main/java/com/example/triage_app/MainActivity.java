package com.example.triage_app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
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
import com.triage.model.Victim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import static java.lang.Thread.sleep;

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

    private String SERVICE_ID = "triage.communication";
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    private String chosenEndpointID;
    private Victim victim = null;
    private String triageSystem = "";
    private Rescuer rescuerData = new Rescuer();
    CustomAdapter customAdapter;
    private ArrayList<Endpoint> endpoints = new ArrayList<>();

    private boolean discovering = false;

    private Victim.TriageColor lastColor = null;

    //Kod do spinnera w widoku victim_content
    String[] spinnerTitles = new String[]{"Czarny", "Czerwony", "Żółty", "Zielony"};
    int[] spinnerImages = new int[]{R.color.colorTriageBlack,
                        R.color.colorTriageRed,
                        R.color.colorTriageYellow,
                        R.color.colorTriageGreen};


    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Endpoint e = new Endpoint();
                    e.setId(endpointId);
                    e.setName(info.getEndpointName());

                    if(e.getName().equals("Kierujacy Akcja Medyczna")){
                        if(triageSystem.isEmpty()) {
                            Nearby.getConnectionsClient(getApplicationContext()).requestConnection(e.getName(), e.getId(), connectionLifecycleCallback);
                            //Toast.makeText(getApplicationContext(), "Wykryto KAM. Łączę się", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else{
                        endpoints.add(e);
                        customAdapter.notifyDataSetChanged();
                    }
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
            try {//próba interpretacji jako poszkodowany
                if(victim!=null) {
                    ByteArrayInputStream bis = new ByteArrayInputStream(payload.asBytes());
                    ObjectInputStream is = new ObjectInputStream(bis);
                    victim = (Victim) is.readObject();

                    updateVictimDetailsInColorChoice(victim);
                }
                else {
                    ByteArrayInputStream bis = new ByteArrayInputStream(payload.asBytes());
                    ObjectInputStream is = new ObjectInputStream(bis);
                    victim = (Victim) is.readObject();

                    showColorChoiceDialog(victim, s);
                }
                return;
            } catch (Exception exception){
                Log.e("Payload", "not a victim");
            }

            try {//próba interpretacji jako system klasyfikacji
                String system = new String( payload.asBytes());//konwersja bajtów na tekst

                ArrayList<String> triageSystems = new ArrayList<>(Arrays.asList("START", "CareFlight", "SIEVE"));
                if(!triageSystems.contains(system)){
                    throw new Exception();
                }
                triageSystem = system;
                ((TextView) findViewById(R.id.details_system_val)).setText(triageSystem);
                ((TextView) findViewById(R.id.list_system_val)).setText(triageSystem);
                Toast.makeText(getApplicationContext(), "Otrzymano system od KAMa: " + triageSystem, Toast.LENGTH_SHORT).show();
                Nearby.getConnectionsClient(getApplicationContext()).disconnectFromEndpoint(s);
                return;
            } catch (Exception exception){
                Log.e("Payload", "error");
            } //nastąpił błąd konwersji
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
                                            //Nearby.getConnectionsClient(getApplicationContext()).disconnectFromEndpoint(endpointId);
                                        })
                                .addOnFailureListener(
                                (Exception e) -> {
                                    //Nearby.getConnectionsClient(getApplicationContext()).disconnectFromEndpoint(endpointId);
                                    Log.e("Payload", e.getMessage());
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
            for (Iterator<Endpoint> iterator = endpoints.iterator(); iterator.hasNext(); ) {
                Endpoint e = iterator.next();
                if (endpointId.equals(e.getId())) {
                    Toast.makeText(getApplicationContext(), "Rozłączono z czujnikiem: "+e.getName(), Toast.LENGTH_SHORT).show();
                    if(lastColor==null){
                        return;
                    }
                    iterator.remove();
                    customAdapter.notifyDataSetChanged();
                    lastColor=null;
                    return;
                }
            }
            Toast.makeText(getApplicationContext(), "Rozłączono z KAMem", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(getApplicationContext(), "Skanowanie w poszukiwaniu KAMa", Toast.LENGTH_SHORT).show();
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
        rescuerData.setId(tm.getDeviceId());

        //final EditText name = new EditText(this);
        //name.setInputType(InputType.TYPE_CLASS_TEXT);


        if (isLoggedIn()) {
            Toast.makeText(getApplicationContext(), "Zalogowany", Toast.LENGTH_SHORT).show();
            Log.e("Login", "Zalogowany");
            rescuerData.setName(getLoggedInName());
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


            String filename = "last_login.txt";

            mLogin.setOnClickListener(v -> {
                if (!mId.getText().toString().isEmpty() && !mPassword.getText().toString().isEmpty()) {
                    Toast.makeText(MainActivity.this,
                            "Zalogowano",
                            Toast.LENGTH_SHORT).show();

                    //zapis do pliku login, hasło, czas w sekundach
                    try {
                        FileOutputStream stream = openFileOutput(filename, Context.MODE_PRIVATE);
                        Date date = new Date();

                        String login = mId.getText().toString() + "\n" +
                                mPassword.getText().toString() + "\n" +
                                date.getTime() + "\n";

                        rescuerData.setName(mId.getText().toString());
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
        String path = getApplicationContext().getFilesDir() + "/" + "last_login.txt";
        File file = new File(path);
        int length = (int) file.length();


        //jakie pliki dostepne
        File dirFiles = getApplicationContext().getFilesDir();
        for (String fname: dirFiles.list())
        {
            Log.e("Pliki", fname);
        }


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

            String[] content_tab = contents.split("\n");
            long oldTime = Long.parseLong(content_tab[2]);
            Date newTime = new Date();

            if (oldTime - newTime.getTime() > 4*60*60) {
                Log.e("Czas","Przekroczono czas");
                return false;
            }

            return true;
        } else {
            Log.e("isLoggedIn", "brak pliku");

            return false;
        }


    }


    private String getLoggedInName() {
        String path = getApplicationContext().getFilesDir() + "/" + "last_login.txt";
        File file = new File(path);
        int length = (int) file.length();


        //jakie pliki dostepne
        File dirFiles = getApplicationContext().getFilesDir();
        for (String fname: dirFiles.list())
        {
            Log.e("Pliki", fname);
        }


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

            String[] content_tab = contents.split("\n");

            return content_tab[0];
        } else {
            Log.e("isLoggedIn", "brak pliku");

            return "";
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
            if(triageSystem.isEmpty()){
                Toast.makeText(getApplicationContext(), "Błąd. Brak systemu triażu od KAM", Toast.LENGTH_SHORT).show();
                return;
            }
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

        ColorAdapter mCustomAdapter = new ColorAdapter(MainActivity.this, spinnerTitles, spinnerImages);
        Spinner mSpinner = findViewById(R.id.spinner);
        mSpinner.setAdapter(mCustomAdapter);


        Button sendButton= findViewById(R.id.sendButton);
        sendButton.setOnClickListener(v -> {
            int id = ((Spinner) findViewById(R.id.spinner)).getSelectedItemPosition();
            switch (id){
                case 0:
                    lastColor = Victim.TriageColor.BLACK;
                    break;
                case 1:
                    lastColor = Victim.TriageColor.RED;
                    break;
                case 2:
                    lastColor = Victim.TriageColor.YELLOW;
                    break;
                case 3:
                    lastColor = Victim.TriageColor.GREEN;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(lastColor);
                oos.flush();
                byte[] data = bos.toByteArray();
                Payload bytesPayload = Payload.fromBytes(data);
                Nearby.getConnectionsClient(getApplicationContext()).sendPayload(chosenEndpointID, bytesPayload)
                        .addOnSuccessListener(
                                (Void unused) -> {
                                    Nearby.getConnectionsClient(getApplicationContext()).disconnectFromEndpoint(chosenEndpointID);
                                    Toast.makeText(getApplicationContext(), "Próba rozłączenia", Toast.LENGTH_SHORT).show();
                                    hideColorChoiceDialog();
                                })
                        .addOnFailureListener(
                                (Exception e) -> {
                                    Toast.makeText(getApplicationContext(), "Błąd przesyłu koloru", Toast.LENGTH_SHORT).show();
                                    lastColor = null;
                                    Log.e("Payload", e.getMessage());
                                });
                //Toast.makeText(getApplicationContext(), "Wysłano", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }

    public void updateSettings() {
        TextView t = findViewById(R.id.transmitter_status_label);
        t.setText(discovering ? "szuka czujników" : "bezczynny");
    }

    private void updateVictimDetailsInColorChoice(Victim v){

        TextView t = findViewById(R.id.breath_val);
        if(v.isBreathing())
            t.setText("tak");
        else
            t.setText("nie");

        t = findViewById(R.id.refill_val);
        t.setText(v.getRespiratoryRate()+"odd./min");

        t = findViewById(R.id.pulse_val);
        t.setText(v.getCapillaryRefillTime()+"s");

        t = findViewById(R.id.walking_val);
        if(v.isWalking())
            t.setText("tak");
        else
            t.setText("nie");

        t = findViewById(R.id.conscious_val);
        switch(v.getConsciousness()){
            case AWAKE: t.setText("przytomny"); break;
            case VERBAL: t.setText("reag. na głos"); break;
            case PAIN: t.setText("reag. na ból"); break;
            case UNRESPONSIVE: t.setText("nieprzytomny"); break;
        }
    }

    private Endpoint findEndpointById(String id){
        for (Iterator<Endpoint> iterator = endpoints.iterator(); iterator.hasNext(); ) {
            Endpoint e = iterator.next();
            if (id.equals(e.getId())) {
                return e;
            }
        }
        return new Endpoint();
    }

    private void showColorChoiceDialog(Victim v, String endpointID){
        chosenEndpointID = endpointID;
        Endpoint end = findEndpointById(chosenEndpointID);
        ((ViewFlipper) findViewById(R.id.layout_manager)).setDisplayedChild(2);
        (findViewById(R.id.toolbar)).setVisibility(View.GONE);

        ((TextView)findViewById(R.id.monitor_id_val)).setText(end.getName());

        updateVictimDetailsInColorChoice(v);

    }

    private void hideColorChoiceDialog(){
        chosenEndpointID = null;
        clearVictimWithDelay(2000);
        ((ViewFlipper) findViewById(R.id.layout_manager)).setDisplayedChild(0);
        (findViewById(R.id.toolbar)).setVisibility(View.VISIBLE);

    }

    private void clearVictimWithDelay(long milis){

        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        victim = null;
                    }
                },
                milis
        );
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
            //  case R.id.action_attributes:
                //vf.setDisplayedChild(2);
                //return true;
            default:
                vf.setDisplayedChild(0);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed(){
        if(((ViewFlipper)findViewById(R.id.layout_manager)).getDisplayedChild() == 2){
            AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);

            mBuilder.setMessage("Czy na pewno chcesz rozłączyć się z czujnikiem?")
                    .setPositiveButton("Tak", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Nearby.getConnectionsClient(getApplicationContext()).disconnectFromEndpoint(chosenEndpointID);
                            hideColorChoiceDialog();
                            Toast.makeText(getApplicationContext(), "Rozłączono", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Nie", null);

            final AlertDialog dialog = mBuilder.create();
            dialog.show();
            return;
        }
        super.onBackPressed();
    }
}
