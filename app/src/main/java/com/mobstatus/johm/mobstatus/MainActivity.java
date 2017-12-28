package com.mobstatus.johm.mobstatus;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.ButterKnife;
import butterknife.BindView;
import butterknife.OnClick;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity {

    private String serverAddress = "ws://192.168.0.114:1450/secret-android-key";

    //Should match the interval in server.js
    private int interval = 600000;

    private String mobStatusMessage = "{ \"service\": \"mobStatus\", \"action\" : \"activate\" }";

    private WebSocketClient wsClient;
    private Runnable textThread;
    private Runnable btnThread;

    private boolean isConnected = false;


    @BindView(R.id.connectBtn)
    Button connectBtn;

    @BindView(R.id.status)
    EditText statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        displayMessage("open");
        updateBtn("Connect");

        //wsConnect();
    }

    @OnClick(R.id.connectBtn)
    public void connectClick(View view){
        Log.d("Button", "Connect Clicked");
        if(!isConnected){
            wsConnect();
        }
        else{
            disconnect();
        }
    }

    private void disconnect(){
        wsClient.close();
    }

    private void displayMessage(String action){

        final int s;

        switch(action){


            case "connecting":
                s = R.string.websocket_connecting;
                break;

            case "connected":
                s = R.string.websocket_connected;
                break;

            case "disconnected":
                s = R.string.websocket_disconnected;
                break;

            case "error_connecting":
                s = R.string.websocket_error_connection;
                break;

            default:
                s = R.string.app_name;
                break;
        }

        runOnUiThread(new Runnable(){
            public void run(){
                TextView textView = (TextView)findViewById(R.id.message);
                textView.setText(s);
            }
        });



    }

    private void isConnected(){

        updateBtn("Disconnect");
        displayMessage("connected");
        startLoop();
    }

    private void isDisconnected(){
        isConnected = false;
        updateBtn("Connect");
        displayMessage("disconnected");
    }

    private String mobStatusMessage(){

        statusText = (EditText)findViewById(R.id.status);
        String msg = statusText.getText().toString();

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = (level / (float)scale) * 100;

        return "{ \"service\": \"mobStatus\", \"action\" : \"activate\", \"message\" : \""+msg+"\", \"battery\" : \""+batteryPct+"\" }";
    }

    private void startLoop(){

       new Timer().scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){

                if(isConnected){
                    Log.d("Message", "sent");
                    wsClient.send(mobStatusMessage());
                }
                else{
                    this.cancel();
                }
            }
        },0,interval);
    }

    private void updateBtn(final String s){

        runOnUiThread(new Runnable(){
            public void run(){
                Button btn = (Button)findViewById(R.id.connectBtn);
                btn.setText(s);
            }
        });

    }


    private void wsConnect(){
        URI uri;

        displayMessage("connecting");

        try{
            uri = new URI(serverAddress);
            Log.d("URI", "Success" );

        }
        catch( URISyntaxException e){
            displayMessage("error_connecting");
            Log.d("URI", "Failure" );
            Log.d("WebSocket", e.getMessage());
            return;
        }

        wsClient = new WebSocketClient(uri) {

            @Override
            public void onOpen(ServerHandshake serverHandShake){

                displayMessage("connected");
                Log.d("WebSocket", "onOpen");
                wsClient.send(mobStatusMessage());
            }

            @Override
            public void onMessage(String s){

                JSONObject js = new JSONObject();

                try{
                    js = new JSONObject(s);
                }
                catch(JSONException e){
                    Log.e("JSONParseError", "Message cannot be parsed");
                }

                try{
                    Log.d("Message", js.get("status").toString());

                    if(js.get("status").toString().equals("connected")){
                        Log.d("Message", js.get("status").toString());
                        isConnected = true;
                        isConnected();
                    }
                }
                catch(JSONException e){
                    Log.e("JSONParseError", "Message cannot be parsed");
                }

            }

            @Override
            public void onClose(int i, String s, boolean b){
                Log.d("onClose", "Closed socket: " + i);
                isDisconnected();
            }

            @Override
            public void onError(Exception e){
                Log.e("onError", "Error " + e.getMessage());
                displayMessage("error_connecting");
            }

        };

        wsClient.connect();
    }


}
