package com.android.demobluetoothsenddata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class MainActivity extends Activity {

    private static int DISCOVERY_REQUEST = 1;

    private Handler handler = new Handler();
    private ArrayList<BluetoothDevice> foundDevices= new ArrayList<>();
    private ArrayAdapter<BluetoothDevice> aa;
    private ListView list;

    private BluetoothAdapter bluetooth;
    private BluetoothSocket socket;
    private UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configureBluetooth();
        setupListView();
        setupSearchButton();
        setupListenButton();
    }

    private void configureBluetooth() {
        bluetooth = BluetoothAdapter.getDefaultAdapter();
    }

    private void setupListenButton() {
        Button listenButton = (Button)findViewById(R.id.button_listen);
        listenButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Intent disc = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                startActivityForResult(disc, DISCOVERY_REQUEST);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DISCOVERY_REQUEST) {
            boolean isDiscoverable = resultCode > 0;
            if (isDiscoverable) {
                String name = "bluetoothserver";
                try {
                    final BluetoothServerSocket btserver =
                            bluetooth.listenUsingRfcommWithServiceRecord(name, uuid);

                    Log.d("BLUETOOTH", "onActivityResult: ");
                    AsyncTask<Integer, Void, BluetoothSocket> acceptThread =
                            new AsyncTask<Integer, Void, BluetoothSocket>() {

                                @Override
                                protected BluetoothSocket doInBackground(Integer... params) {
                                    try {
                                        socket = btserver.accept(params[0]*1000);
                                        return socket;
                                    } catch (IOException e) {
                                        Log.d("BLUETOOTH", e.getMessage());
                                    }
                                    return null;
                                }

                                @Override
                                protected void onPostExecute(BluetoothSocket result) {
                                    if (result != null)
                                        switchUI();
                                }
                            };
                    acceptThread.execute(resultCode);
                } catch (IOException e) {
                    Log.d("BLUETOOTH", e.getMessage());
                }
            }
        }
    }

    private void setupListView() {
        /*BluetoothDevice device1 = bluetooth.getRemoteDevice(":");
        foundDevices.add(device1);*/
        aa = new ArrayAdapter<BluetoothDevice>(this,
                android.R.layout.simple_list_item_1,
                foundDevices);
        list = (ListView)findViewById(R.id.list_discovered);
        list.setAdapter(aa);

        list.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View view,
                                    int index, long arg3) {
                AsyncTask<Integer, Void, Void> connectTask =
                        new AsyncTask<Integer, Void, Void>() {
                            @Override
                            protected Void doInBackground(Integer... params) {
                                try {
                                    BluetoothDevice device = foundDevices.get(params[0]);
                                    socket = device.createRfcommSocketToServiceRecord(uuid);
                                    socket.connect();
                                    manageConnectedSocket()
                                    Log.d("BLUETOOTH_CLIENT", "doInBackground: ");
                                } catch (IOException e) {
                                    Log.d("BLUETOOTH_CLIENT", e.getMessage());
                                }
                                return null;
                            }

                            @Override
                            protected void onPostExecute(Void result) {
                                //switchUI();
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            }
                        };
                connectTask.execute(index);
            }
        });
    }

    private void setupSearchButton() {
        Button searchButton = (Button)findViewById(R.id.button_search);

        searchButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                registerReceiver(discoveryResult,
                        new IntentFilter(BluetoothDevice.ACTION_FOUND));

                if (!bluetooth.isDiscovering()) {
                    foundDevices.clear();
                    bluetooth.startDiscovery();
                }
            }
        });
    }

    private void switchUI() {
        final TextView messageText = (TextView)findViewById(R.id.text_messages);
        final EditText textEntry = (EditText)findViewById(R.id.text_message);

        messageText.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        textEntry.setEnabled(true);

        textEntry.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                    sendMessage(socket, textEntry.getText().toString());
                    textEntry.setText("");
                    return true;
                }
                return false;
            }
        });
        BluetoothSocketListener bsl = new BluetoothSocketListener(socket, handler, messageText);
        Thread messageListener = new Thread(bsl);
        messageListener.start();
    }

    private void sendMessage(BluetoothSocket socket, String msg) {
        OutputStream outStream;
        try {
            outStream = socket.getOutputStream();
            byte[] byteString = (msg + " ").getBytes();
            byteString[byteString.length - 1] = 0;
            outStream.write(byteString);
            Log.d("BLUETOOTH_COMMS", "sendMessage: ");
        } catch (IOException e) {
            Log.d("BLUETOOTH_COMMS", e.getMessage());
        }
    }

    BroadcastReceiver discoveryResult = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            BluetoothDevice remoteDevice;
            remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d("HoangCV", "onReceive: "+remoteDevice);
            //if (bluetooth.getBondedDevices().contains(remoteDevice)) {
                foundDevices.add(remoteDevice);

                aa.notifyDataSetChanged();
            //}
        }
    };

    private class MessagePoster implements Runnable {
        private TextView textView;
        private String message;

        public MessagePoster(TextView textView, String message) {
            this.textView = textView;
            this.message = message;
        }

        public void run() {
            textView.setText(message);
        }
    }

    private class BluetoothSocketListener implements Runnable {

        private BluetoothSocket socket;
        private TextView textView;
        private Handler handler;

        public BluetoothSocketListener(BluetoothSocket socket,
                                       Handler handler, TextView textView) {
            this.socket = socket;
            this.textView = textView;
            this.handler = handler;
        }

        public void run() {
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            try {
                InputStream instream = socket.getInputStream();
                int bytesRead = -1;
                String message = "";
                while (true) {
                    message = "";
                    bytesRead = instream.read(buffer);
                    if (bytesRead != -1) {
                        while ((bytesRead==bufferSize)&&(buffer[bufferSize-1] != 0)) {
                            message = message + new String(buffer, 0, bytesRead);
                            bytesRead = instream.read(buffer);
                        }
                        message = message + new String(buffer, 0, bytesRead - 1);

                        handler.post(new MessagePoster(textView, message));
                        socket.getInputStream();
                    }
                }
            } catch (IOException e) {
                Log.d("BLUETOOTH_COMMS", e.getMessage());
            }
        }
    }
}