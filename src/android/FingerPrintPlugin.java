// Copyright 2016 Franco Bugnano
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ifabula.fingerprintplugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import android.widget.Toast;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.annotation.SuppressLint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class FingerPrintPlugin extends CordovaPlugin {
	public static final String TAG = "CordovaNetworkingBluetooth";
	public static final String SERVICE_NAME = "CordovaNetworkingBluetooth";
	public static final int REQUEST_ENABLE_BT = 1773;
	public static final int REQUEST_DISCOVERABLE_BT = 1885;
	public static final int START_DISCOVERY_REQ_CODE = 1997;
	public static final int READ_BUFFER_SIZE = 73728;

	public class SocketSendData {
		public CallbackContext mCallbackContext;
		public BluetoothSocket mSocket;
		public byte[] mData;

		public SocketSendData(CallbackContext callbackContext, BluetoothSocket socket, byte[] data) {
			this.mCallbackContext = callbackContext;
			this.mSocket = socket;
			this.mData = data;
		}
	}

	public BluetoothAdapter mBluetoothAdapter = null;
	public ConcurrentHashMap<Integer, CallbackContext> mContextForActivity = new ConcurrentHashMap<Integer, CallbackContext>();
	public ConcurrentHashMap<Integer, CallbackContext> mContextForPermission = new ConcurrentHashMap<Integer, CallbackContext>();
	public CallbackContext mContextForAdapterStateChanged = null;
	public CallbackContext mContextForDeviceAdded = null;
	public CallbackContext mContextForReceive = null;
	public CallbackContext mContextForReceiveError = null;
	public CallbackContext mContextForAccept = null;
	public CallbackContext mContextForAcceptError = null;
	public CallbackContext mContextForEnable = null;
	public CallbackContext mContextForDisable = null;
	public boolean mDeviceAddedRegistered = false;
	public int mPreviousScanMode = BluetoothAdapter.SCAN_MODE_NONE;
	public AtomicInteger mSocketId = new AtomicInteger(1);
	public ConcurrentHashMap<Integer, BluetoothSocket> mClientSockets = new ConcurrentHashMap<Integer, BluetoothSocket>();
	public ConcurrentHashMap<Integer, BluetoothServerSocket> mServerSockets = new ConcurrentHashMap<Integer, BluetoothServerSocket>();
	public LinkedBlockingQueue<SocketSendData> mSendQueue = new LinkedBlockingQueue<SocketSendData>();

    public CallbackContext initCallbackContext;
     // Member object for the chat services
    public BluetoothReaderService mChatService = null;
        // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    // private static final int REQUEST_ENABLE_BT = 2;

    //directory for saving the fingerprint images
    private String sDirectory = "";

     // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    // public static final int START_DISCOVERY_REQ_CODE = 1997;

     //default image size
    public static final int IMG_WIDTH = 256;
    public static final int IMG_HEIGHT = 288;
    public static final int IMG_SIZE = IMG_WIDTH * IMG_HEIGHT;
    public static final int WSQBUFSIZE = 200000;

    //other image size
    private int imgSize;
    public byte mBat[] = new byte[2];  // data of battery status
    public byte mUpImage[] = new byte[73728]; // image data
    public int mUpImageSize = 0;
    public int mUpImageCount = 0;
    public static final int IMG200 = 200;
    public static final int IMG288 = 288;
    public static final int IMG360 = 360;

    //definition of commands
    private final static byte CMD_PASSWORD = 0x01;    //Password
    private final static byte CMD_ENROLID = 0x02;        //Enroll in Device
    private final static byte CMD_VERIFY = 0x03;        //Verify in Device
    private final static byte CMD_IDENTIFY = 0x04;    //Identify in Device
    private final static byte CMD_DELETEID = 0x05;    //Delete in Device
    private final static byte CMD_CLEARID = 0x06;        //Clear in Device

    private final static byte CMD_ENROLHOST = 0x07;    //Enroll to Host
    private final static byte CMD_CAPTUREHOST = 0x08;    //Caputre to Host
    private final static byte CMD_MATCH = 0x09;        //Match
    private final static byte CMD_GETIMAGE = 0x30;      //GETIMAGE
    private final static byte CMD_GETCHAR = 0x31;       //GETDATA

    private byte mDeviceCmd = 0x00;
    private boolean mIsWork = false;
    private byte mCmdData[] = new byte[10240];
    private int mCmdSize = 0;

    private Timer mTimerTimeout = null;
    private TimerTask mTaskTimeout = null;
    private Handler mHandlerTimeout;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);

		this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (this.mBluetoothAdapter != null) {
			this.mPreviousScanMode = this.mBluetoothAdapter.getScanMode();
		}

		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				writeLoop();
			}
		});
	}

	@Override
	public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
		IntentFilter filter;

		if (this.mBluetoothAdapter == null) {
			callbackContext.error("Device does not support Bluetooth");
			return false;
		}

		if (action.equals("registerAdapterStateChanged")) {
			this.mContextForAdapterStateChanged = callbackContext;

			filter = new IntentFilter();
			filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
			filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
			filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
			filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
			cordova.getActivity().registerReceiver(this.mReceiver, filter);

			return true;
		} else if (action.equals("registerDeviceAdded")) {
			this.mContextForDeviceAdded = callbackContext;
			return true;
		} else if (action.equals("registerReceive")) {
			this.mContextForReceive = callbackContext;
			return true;
		} else if (action.equals("registerReceiveError")) {
			this.mContextForReceiveError = callbackContext;
			return true;
		} else if (action.equals("registerAccept")) {
			this.mContextForAccept = callbackContext;
			return true;
		} else if (action.equals("registerAcceptError")) {
			this.mContextForAcceptError = callbackContext;
			return true;
		} else if (action.equals("getAdapterState")) {
			this.getAdapterState(callbackContext, false);
			return true;
		} else if (action.equals("requestEnable")) {
			if (!this.mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.prepareActivity(action, args, callbackContext, enableBtIntent, REQUEST_ENABLE_BT);
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("enable")) {
			// If there already is another enable action pending, call the error callback in order
			// to notify that the previous action has been cancelled
			if (this.mContextForEnable != null) {
				this.mContextForEnable.error(1);
				this.mContextForEnable = null;
			}

			if (!this.mBluetoothAdapter.isEnabled()) {
				if (!this.mBluetoothAdapter.enable()) {
					callbackContext.error(0);
				} else {
					// Save the context, in order to send the result once the action has been completed
					this.mContextForEnable = callbackContext;
				}
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("disable")) {
			// If there already is another disable action pending, call the error callback in order
			// to notify that the previous action has been cancelled
			if (this.mContextForDisable != null) {
				this.mContextForDisable.error(1);
				this.mContextForDisable = null;
			}

			if (this.mBluetoothAdapter.isEnabled()) {
				if (!this.mBluetoothAdapter.disable()) {
					callbackContext.error(0);
				} else {
					// Save the context, in order to send the result once the action has been completed
					this.mContextForDisable = callbackContext;
				}
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("getDevice")) {
			String address = args.getString(0);
			BluetoothDevice device = this.mBluetoothAdapter.getRemoteDevice(address);
			callbackContext.success(this.getDeviceInfo(device));
			return true;
		} else if (action.equals("getDevices")) {
			Set<BluetoothDevice> devices = this.mBluetoothAdapter.getBondedDevices();
			JSONArray deviceInfos = new JSONArray();
			for (BluetoothDevice device : devices) {
				deviceInfos.put(this.getDeviceInfo(device));
			}
			callbackContext.success(deviceInfos);
			return true;
		} else if (action.equals("startDiscovery")) {
			// Automatically cancel any previous discovery
			if (this.mBluetoothAdapter.isDiscovering()) {
				this.mBluetoothAdapter.cancelDiscovery();
			}

			if (cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
				this.startDiscovery(callbackContext);
			} else {
				this.getPermission(callbackContext, START_DISCOVERY_REQ_CODE, Manifest.permission.ACCESS_COARSE_LOCATION);
			}
			return true;
		} else if (action.equals("stopDiscovery")) {
			if (this.mBluetoothAdapter.isDiscovering()) {
				if (this.mBluetoothAdapter.cancelDiscovery()) {
					callbackContext.success();
				} else {
					callbackContext.error(0);
				}
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("requestDiscoverable")) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			this.prepareActivity(action, args, callbackContext, discoverableIntent, REQUEST_DISCOVERABLE_BT);
			return true;
		} else if (action.equals("connect")) {
			final String address = args.getString(0);
			final String uuid = args.getString(1);
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					int socketId;
					BluetoothSocket socket;

					try {
						BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
						socket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid));

						// Note: You should always ensure that the device is not performing
						// device discovery when you call connect().
						// If discovery is in progress, then the connection attempt will be
						// significantly slowed and is more likely to fail.
						mBluetoothAdapter.cancelDiscovery();

						socket.connect();

						socketId = mSocketId.getAndIncrement();
						mClientSockets.put(socketId, socket);
						callbackContext.success(socketId);
					} catch (NullPointerException e) {
						callbackContext.error(e.getMessage());
						return;
					} catch (IllegalArgumentException e) {
						callbackContext.error(e.getMessage());
						return;
					} catch (IOException e) {
						callbackContext.error(e.getMessage());
						return;
					}

					// Now that the connection has been made, begin the read loop
					readLoop(socketId, socket);
				}
			});
			return true;
		} else if (action.equals("close")) {
			int socketId = args.getInt(0);
			BluetoothSocket socket = this.mClientSockets.remove(socketId);
			if (socket != null) {
				// The socketId refers to a client socket
				try {
					socket.close();
					callbackContext.success();
				} catch (IOException e) {
					callbackContext.error(e.getMessage());
				}
			} else {
				BluetoothServerSocket serverSocket = this.mServerSockets.remove(socketId);
				if (serverSocket != null) {
					// The socketId refers to a server socket
					try {
						serverSocket.close();
						callbackContext.success();
					} catch (IOException e) {
						callbackContext.error(e.getMessage());
					}
				} else {
					// Closing an already closed socket is not an error
					callbackContext.success();
				}
			}
			return true;
		} else if (action.equals("send")) {
			int socketId = args.getInt(0);
			byte[] data = null;
			int size = 0;
			int sendsize = 9 + size;
			byte[] sendbuf = new byte[sendsize];
			sendbuf[0] = 'F';
			sendbuf[1] = 'T';
			sendbuf[2] = 0;
			sendbuf[3] = 0;
			sendbuf[4] = CMD_GETIMAGE;
			sendbuf[5] = (byte) (size);
			sendbuf[6] = (byte) (size >> 8);
			if (size > 0) {
				for (int i = 0; i < size; i++) {
					sendbuf[7 + i] = data[i];
				}
			}
			int sum = calcCheckSum(sendbuf, (7 + size));
			sendbuf[7 + size] = (byte) (sum);
			sendbuf[8 + size] = (byte) (sum >> 8);

			mIsWork = true;
			//TimeOutStart();
			mDeviceCmd = CMD_GETIMAGE;
			mCmdSize = 0;

			BluetoothSocket socket = this.mClientSockets.get(socketId);
			if (socket != null) {
				try {
					// The send operation occurs in a separate thread
					this.mSendQueue.put(new SocketSendData(callbackContext, socket, sendbuf));
				} catch (InterruptedException e) {
					callbackContext.error(e.getMessage());
				}
			} else {
				callbackContext.error("Invalid socketId");
			}
			return true;
		} else if (action.equals("listenUsingRfcomm")) {
			final String uuid = args.getString(0);
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					int serverSocketId;
					BluetoothServerSocket serverSocket;

					try {
						serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, UUID.fromString(uuid));
						serverSocketId = mSocketId.getAndIncrement();
						mServerSockets.put(serverSocketId, serverSocket);
						callbackContext.success(serverSocketId);
					} catch (NullPointerException e) {
						callbackContext.error(e.getMessage());
						return;
					} catch (IllegalArgumentException e) {
						callbackContext.error(e.getMessage());
						return;
					} catch (IOException e) {
						callbackContext.error(e.getMessage());
						return;
					}

					// Now that the server socket has been made, begin the accept loop
					acceptLoop(serverSocketId, serverSocket);
				}
			});
			return true;
		} else {
			callbackContext.error("Invalid action");
			return false;
		}
	}

	public void getAdapterState(CallbackContext callbackContext, boolean keepCallback) {
		PluginResult pluginResult;

		try {
			JSONObject adapterState = new JSONObject();
			adapterState.put("address", this.mBluetoothAdapter.getAddress());
			adapterState.put("name", this.mBluetoothAdapter.getName());
			adapterState.put("enabled", this.mBluetoothAdapter.isEnabled());
			adapterState.put("discovering", this.mBluetoothAdapter.isDiscovering());
			adapterState.put("discoverable", this.mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);

            pluginResult = new PluginResult(PluginResult.Status.OK, adapterState);
            pluginResult.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(pluginResult);
		} catch (JSONException e) {
            pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            pluginResult.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(pluginResult);
		}
	}

	public JSONObject getDeviceInfo(BluetoothDevice device) throws JSONException {
		JSONObject deviceInfo = new JSONObject();

		deviceInfo.put("address", device.getAddress());
		deviceInfo.put("name", device.getName());
		deviceInfo.put("paired", device.getBondState() == BluetoothDevice.BOND_BONDED);

		JSONArray deviceUUIDs = new JSONArray();
		ParcelUuid[] uuids = device.getUuids();
		if (uuids != null) {
			for (int i = 0; i < uuids.length; i++) {
				deviceUUIDs.put(uuids[i].toString());
			}
		}
		deviceInfo.put("uuids", deviceUUIDs);

		return deviceInfo;
	}

	public void prepareActivity(String action, CordovaArgs args, CallbackContext callbackContext, Intent intent, int requestCode) {
		// If there already is another activity with this request code, call the error callback in order
		// to notify that the activity has been cancelled
		if (this.mContextForActivity.containsKey(requestCode)) {
			callbackContext.error("Attempted to start the same activity twice");
			return;
		}

		// Store the callbackContext, in order to send the result once the activity has been completed
		this.mContextForActivity.put(requestCode, callbackContext);

		// Store the callbackContext, in order to send the result once the activity has been completed
		cordova.startActivityForResult(this, intent, requestCode);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		CallbackContext callbackContext = this.mContextForActivity.remove(requestCode);

		if (callbackContext != null) {
			if (resultCode == Activity.RESULT_CANCELED) {
				callbackContext.error(0);
			} else {
				callbackContext.success();
			}
		} else {
			// TO DO -- This may be a bug on the JavaScript side, as we get here only if the
			// activity has been started twice, before waiting the completion of the first one.
			Log.e(TAG, "BUG: onActivityResult -- (callbackContext == null)");
		}
	}

	public final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			PluginResult pluginResult;

			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
				int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);

				// If there was an enable request pending, send the result
				if ((previousState == BluetoothAdapter.STATE_TURNING_ON) && (mContextForEnable != null)) {
					if (state == BluetoothAdapter.STATE_ON) {
						mContextForEnable.success();
					} else {
						mContextForEnable.error(2);
					}
					mContextForEnable = null;
				}

				// If there was a disable request pending, send the result
				if ((previousState == BluetoothAdapter.STATE_TURNING_OFF) && (mContextForDisable != null)) {
					if (state == BluetoothAdapter.STATE_OFF) {
						mContextForDisable.success();
					} else {
						mContextForDisable.error(2);
					}
					mContextForDisable = null;
				}

				// Send the state changed event only if the state is not a transitioning one
				if ((state == BluetoothAdapter.STATE_OFF) || (state == BluetoothAdapter.STATE_ON)) {
					getAdapterState(mContextForAdapterStateChanged, true);
				}
			} else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED) || action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
				getAdapterState(mContextForAdapterStateChanged, true);
			} else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
				try {
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					JSONObject deviceInfo = getDeviceInfo(device);

					pluginResult = new PluginResult(PluginResult.Status.OK, deviceInfo);
					pluginResult.setKeepCallback(true);
					mContextForDeviceAdded.sendPluginResult(pluginResult);
				} catch (JSONException e) {
					pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
					pluginResult.setKeepCallback(true);
					mContextForDeviceAdded.sendPluginResult(pluginResult);
				}
			} else if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
				// BUG: The documented EXTRA_PREVIOUS_SCAN_MODE field of the intent is not implemented on Android.
				// For details see:
				// http://stackoverflow.com/questions/30553911/extra-previous-scan-mode-always-returns-an-error-for-android-bluetooth
				// As a workaround, the previous scan mode is handled manually here
				int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);

				// Report only the transitions from/to SCAN_MODE_CONNECTABLE_DISCOVERABLE
				if ((scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) || (mPreviousScanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)) {
					getAdapterState(mContextForAdapterStateChanged, true);
				}
				mPreviousScanMode = scanMode;
			}
		}
	};

	public void readLoop(int socketId, BluetoothSocket socket) {
		byte[] readBuffer = new byte[READ_BUFFER_SIZE];
		byte[] data;
		ArrayList<PluginResult> multipartMessages;
		PluginResult pluginResult;

		try {
			InputStream stream = socket.getInputStream();
			int bytesRead;

			while (socket.isConnected()) {
				bytesRead = stream.read(readBuffer);
				if (bytesRead < 0) {
					throw new IOException("Disconnected");
				} else if (bytesRead > 0) {
					if (mDeviceCmd == CMD_GETIMAGE) { 
						memcpy(mUpImage, mUpImageSize, readBuffer, 0, bytesRead);
						mUpImageSize = mUpImageSize + bytesRead;
						if (mUpImageSize >= 36864) {

							File file = new File("/sdcard/test.raw");
							try {
								file.createNewFile();
								FileOutputStream out = new FileOutputStream(file);
								out.write(mUpImage);
								out.close();
							} catch (IOException e) {
								e.printStackTrace();
							}

							byte[] bmpdata = getFingerprintImage(mUpImage, 256, 360, 0/*18*/);
							
							Bitmap image = BitmapFactory.decodeByteArray(bmpdata, 0, bmpdata.length);
							saveJPGimage(image);

							byte[] inpdata = new byte[92160];
							int inpsize = 92160;
							System.arraycopy(bmpdata, 1078, inpdata, 0, inpsize);
							SaveWsqFile(inpdata, inpsize, "fingerprint.wsq");

							Log.d(TAG, "bmpdata.length:" + bmpdata.length);
							
							mUpImageSize = 0;
							mUpImageCount = mUpImageCount + 1;
							mIsWork = false;

							data = Arrays.copyOf(readBuffer, mUpImageSize);
							multipartMessages = new ArrayList<PluginResult>();
							multipartMessages.add(new PluginResult(PluginResult.Status.OK, socketId));
							multipartMessages.add(new PluginResult(PluginResult.Status.OK, data));
							pluginResult = new PluginResult(PluginResult.Status.OK, multipartMessages);
							pluginResult.setKeepCallback(true);
							this.mContextForReceive.sendPluginResult(pluginResult);
						}
					}
					
				}
			}
		} catch (IOException e) {
			try {
				JSONObject info = new JSONObject();
				info.put("socketId", socketId);
				info.put("errorMessage", e.getMessage());
				pluginResult = new PluginResult(PluginResult.Status.OK, info);
				pluginResult.setKeepCallback(true);
				this.mContextForReceiveError.sendPluginResult(pluginResult);
			} catch (JSONException ex) {}
		}

		try {
			socket.close();
		} catch (IOException e) {}

		// The socket has been closed, remove its socketId
		this.mClientSockets.remove(socketId);
	}

	public void acceptLoop(int serverSocketId, BluetoothServerSocket serverSocket) {
		int clientSocketId;
		BluetoothSocket clientSocket;
		ArrayList<PluginResult> multipartMessages;
		PluginResult pluginResult;

		try {
			while (true) {
				clientSocket = serverSocket.accept();
				if (clientSocket == null) {
					throw new IOException("Disconnected");
				}

				clientSocketId = this.mSocketId.getAndIncrement();
				this.mClientSockets.put(clientSocketId, clientSocket);

				multipartMessages = new ArrayList<PluginResult>();
				multipartMessages.add(new PluginResult(PluginResult.Status.OK, serverSocketId));
				multipartMessages.add(new PluginResult(PluginResult.Status.OK, clientSocketId));
				pluginResult = new PluginResult(PluginResult.Status.OK, multipartMessages);
				pluginResult.setKeepCallback(true);
				this.mContextForAccept.sendPluginResult(pluginResult);

				this.newReadLoopThread(clientSocketId, clientSocket);
			}
		} catch (IOException e) {
			try {
				JSONObject info = new JSONObject();
				info.put("socketId", serverSocketId);
				info.put("errorMessage", e.getMessage());
				pluginResult = new PluginResult(PluginResult.Status.OK, info);
				pluginResult.setKeepCallback(true);
				this.mContextForAcceptError.sendPluginResult(pluginResult);
			} catch (JSONException ex) {}
		}

		try {
			serverSocket.close();
		} catch (IOException e) {}

		// The socket has been closed, remove its socketId
		this.mServerSockets.remove(serverSocketId);
	}

	public void newReadLoopThread(final int socketId, final BluetoothSocket socket) {
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				readLoop(socketId, socket);
			}
		});
	}

	public void writeLoop() {
		SocketSendData sendData;

		try {
			while (true) {
				sendData = this.mSendQueue.take();

				try {
					sendData.mSocket.getOutputStream().write(sendData.mData);
					sendData.mCallbackContext.success(sendData.mData.length);
				} catch (IOException e) {
					sendData.mCallbackContext.error(e.getMessage());
				}
			}
		} catch (InterruptedException e) {}
	}

	public void startDiscovery(CallbackContext callbackContext) {
		if (!this.mDeviceAddedRegistered) {
			IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			cordova.getActivity().registerReceiver(this.mReceiver, filter);
			this.mDeviceAddedRegistered = true;
		}

		if (this.mBluetoothAdapter.startDiscovery()) {
			callbackContext.success();
		} else {
			callbackContext.error(0);
		}
	}

	public void getPermission(CallbackContext callbackContext, int requestCode, String permission) {
		// If there already is another permission request with this request code, call the error callback in order
		// to notify that the request has been cancelled
		if (this.mContextForPermission.containsKey(requestCode)) {
			callbackContext.error("Attempted to request the same permission twice");
			return;
		}

		// Store the callbackContext, in order to send the result once the activity has been completed
		this.mContextForPermission.put(requestCode, callbackContext);

		cordova.requestPermission(this, requestCode, permission);
	}

	@Override
	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
		CallbackContext callbackContext = this.mContextForPermission.remove(requestCode);

		if (requestCode == START_DISCOVERY_REQ_CODE) {
			if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
				this.startDiscovery(callbackContext);
			} else {
				callbackContext.error(0);
			}
		}
	}

    // The Handler that gets information back from the BluetoothChatService
    @SuppressLint("HandlerLeak")
    public final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                    case BluetoothReaderService.STATE_CONNECTED:
                        break;
                    case BluetoothReaderService.STATE_CONNECTING:                        
                        break;
                    case BluetoothReaderService.STATE_LISTEN:
                    case BluetoothReaderService.STATE_NONE:
                        break;
                }
                break;
                case MESSAGE_WRITE:
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    if (readBuf.length > 0) {
                        if (readBuf[0] == (byte) 0x1b) {
                            AddStatusListHex(readBuf, msg.arg1);
                        } else {
                            ReceiveCommand(readBuf, msg.arg1);
                        }
                    }
                    break;
            }
        }
    };

    private void AddStatusListHex(byte[] data, int size) {
        String text = "";
        for (int i = 0; i < size; i++) {
            text = text + " " + Integer.toHexString(data[i] & 0xFF).toUpperCase() + "  ";
        }
        
    }

     /**
     * Received the response from the device
     * @param databuf the data package response from the device
     * @param datasize the size of the data package
     */
    private void ReceiveCommand(byte[] databuf, int datasize) {

        PluginResult pluginResult;
        memcpy(mUpImage, mUpImageSize, databuf, 0, datasize);
        mUpImageSize = mUpImageSize + datasize;
        if (mUpImageSize >= 36864) {
            File file = new File("/sdcard/test.raw");
            try {
                file.createNewFile();
                FileOutputStream out = new FileOutputStream(file);
                out.write(mUpImage);
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            byte[] bmpdata = getFingerprintImage(mUpImage, 256, 288, 0/*18*/);
            
            Bitmap image = BitmapFactory.decodeByteArray(bmpdata, 0, bmpdata.length);
            saveJPGimage(image);

            byte[] inpdata = new byte[73728];
            int inpsize = 73728;
            System.arraycopy(bmpdata, 1078, inpdata, 0, inpsize);
            SaveWsqFile(inpdata, inpsize, "fingerprint.wsq");

            Log.d(TAG, "bmpdata.length:" + bmpdata.length);
            
            mUpImageSize = 0;
            mUpImageCount = mUpImageCount + 1;
            mIsWork = false;
            
            try{
                JSONObject returnObj = new JSONObject();
                returnObj.put("result", "successsss.....");

                pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
                pluginResult.setKeepCallback(true);  
            }catch(Exception e){

            }   

        }
    }

     /**
     * method for saving the fingerprint image as JPG
     * @param bitmap bitmap image
     */
    public void saveJPGimage(Bitmap bitmap) {
        String dir = sDirectory;
        String imageFileName = String.valueOf(System.currentTimeMillis());

        try {
            File file = new File(dir + imageFileName + ".jpg");
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

     /**
     * generate the fingerprint image
     * @param data image data
     * @param width width of the image
     * @param height height of the image
     * @param offset default setting as 0
     * @return bitmap image data
     */
    public byte[] getFingerprintImage(byte[] data, int width, int height, int offset) {
        if (data == null) {
            return null;
        }
        byte[] imageData = new byte[width * height];
        for (int i = 0; i < (width * height / 2); i++) {
            imageData[i * 2] = (byte) (data[i + offset] & 0xf0);
            imageData[i * 2 + 1] = (byte) (data[i + offset] << 4 & 0xf0);
        }
        byte[] bmpData = toBmpByte(width, height, imageData);
        return bmpData;
    }

    /**
     * method of saving the image into WSQ format
     * @param rawdata raw image data.
     * @param rawsize size of the raw image data.
     * @param filename the file name of the image.
     */
    public void SaveWsqFile(byte[] rawdata, int rawsize, String filename) {
        byte[] outdata = new byte[rawsize];
        int[] outsize = new int[1];

        if (rawsize == 73728) {
            wsq.getInstance().RawToWsq(rawdata, rawsize, 256, 288, outdata, outsize, 2.833755f);
        } else if (rawsize == 92160) {
            wsq.getInstance().RawToWsq(rawdata, rawsize, 256, 360, outdata, outsize, 2.833755f);
        }

        try {
            File fs = new File("/sdcard/" + filename);
            if (fs.exists()) {
                fs.delete();
            }
            new File("/sdcard/" + filename);
            RandomAccessFile randomFile = new RandomAccessFile("/sdcard/" + filename, "rw");
            long fileLength = randomFile.length();
            randomFile.seek(fileLength);
            randomFile.write(outdata, 0, outsize[0]);
            randomFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

     /**
     * Create directory folder for storing the images
     */
    public void CreateDirectory() {
        sDirectory = Environment.getExternalStorageDirectory() + "/Fingerprint Images/";
        File destDir = new File(sDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

    }

    /**
     * method of copying the byte[] data with specific length
     * @param dstbuf byte[] for storing the copied data with specific length
     * @param dstoffset the starting point for storing
     * @param srcbuf the source byte[] used for copying.
     * @param srcoffset the starting point for copying
     * @param size the length required to copy
     */
    private void memcpy(byte[] dstbuf, int dstoffset, byte[] srcbuf, int srcoffset, int size) {
        for (int i = 0; i < size; i++) {
            dstbuf[dstoffset + i] = srcbuf[srcoffset + i];
        }
    }

    /**
     * Generate the command package sending via bluetooth
     * @param cmdid command code for different function achieve.
     * @param data the required data need to send to the device
     * @param size the size of the byte[] data
     */
    private void SendCommand(byte cmdid, byte[] data, int size) {
        if (mIsWork) return;

        int sendsize = 9 + size;
        byte[] sendbuf = new byte[sendsize];
        sendbuf[0] = 'F';
        sendbuf[1] = 'T';
        sendbuf[2] = 0;
        sendbuf[3] = 0;
        sendbuf[4] = cmdid;
        sendbuf[5] = (byte) (size);
        sendbuf[6] = (byte) (size >> 8);
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                sendbuf[7 + i] = data[i];
            }
        }
        int sum = calcCheckSum(sendbuf, (7 + size));
        sendbuf[7 + size] = (byte) (sum);
        sendbuf[8 + size] = (byte) (sum >> 8);

        mIsWork = true;
        //TimeOutStart();
        mDeviceCmd = cmdid;
        mCmdSize = 0;
        this.mChatService.write(sendbuf);
    }

    /**
     * calculate the check sum of the byte[]
     * @param buffer byte[] required for calculating
     * @param size the size of the byte[]
     * @return the calculated check sum
     */
    private int calcCheckSum(byte[] buffer, int size) {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum = sum + buffer[i];
        }
        return (sum & 0x00ff);
    }

    private byte[] changeByte(int data) {
        byte b4 = (byte) ((data) >> 24);
        byte b3 = (byte) (((data) << 8) >> 24);
        byte b2 = (byte) (((data) << 16) >> 24);
        byte b1 = (byte) (((data) << 24) >> 24);
        byte[] bytes = {b1, b2, b3, b4};
        return bytes;
    }

     /**
     * stat the timer for counting
     */
    public void TimeOutStart() {
        if (mTimerTimeout != null) {
            return;
        }
        if (initCallbackContext == null) {
            return;
        }
        mTimerTimeout = new Timer();
        mHandlerTimeout = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                PluginResult pluginResult;
                TimeOutStop();
                if (mIsWork) {
                    mIsWork = false;

                    // Enroll timeout
                    try{
                        JSONObject returnObj = new JSONObject();
                        returnObj.put("result", false);
                        returnObj.put("message", "timeout...");
                        pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
                        pluginResult.setKeepCallback(true);
                        initCallbackContext.sendPluginResult(pluginResult);
                    }catch(Exception e){

                    }
                    
                    //AddStatusList("Time Out");
                }else{

                }
                super.handleMessage(msg);
                
            }
        };
        mTaskTimeout = new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                mHandlerTimeout.sendMessage(message);
            }
        };
        mTimerTimeout.schedule(mTaskTimeout, 10000, 10000);
    }

    /**
     * stop the timer
     */
    public void TimeOutStop() {
        if (mTimerTimeout != null) {
            mTimerTimeout.cancel();
            mTimerTimeout = null;
            mTaskTimeout.cancel();
            mTaskTimeout = null;
        }
    }

    /**
     * generate the image data into Bitmap format
     * @param width width of the image
     * @param height height of the image
     * @param data image data
     * @return bitmap image data
     */
    private byte[] toBmpByte(int width, int height, byte[] data) {
        byte[] buffer = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            int bfType = 0x424d;
            int bfSize = 54 + 1024 + width * height;
            int bfReserved1 = 0;
            int bfReserved2 = 0;
            int bfOffBits = 54 + 1024;

            dos.writeShort(bfType);
            dos.write(changeByte(bfSize), 0, 4);
            dos.write(changeByte(bfReserved1), 0, 2);
            dos.write(changeByte(bfReserved2), 0, 2);
            dos.write(changeByte(bfOffBits), 0, 4);

            int biSize = 40;
            int biWidth = width;
            int biHeight = height;
            int biPlanes = 1;
            int biBitcount = 8;
            int biCompression = 0;
            int biSizeImage = width * height;
            int biXPelsPerMeter = 0;
            int biYPelsPerMeter = 0;
            int biClrUsed = 256;
            int biClrImportant = 0;

            dos.write(changeByte(biSize), 0, 4);
            dos.write(changeByte(biWidth), 0, 4);
            dos.write(changeByte(biHeight), 0, 4);
            dos.write(changeByte(biPlanes), 0, 2);
            dos.write(changeByte(biBitcount), 0, 2);
            dos.write(changeByte(biCompression), 0, 4);
            dos.write(changeByte(biSizeImage), 0, 4);
            dos.write(changeByte(biXPelsPerMeter), 0, 4);
            dos.write(changeByte(biYPelsPerMeter), 0, 4);
            dos.write(changeByte(biClrUsed), 0, 4);
            dos.write(changeByte(biClrImportant), 0, 4);

            byte[] palatte = new byte[1024];
            for (int i = 0; i < 256; i++) {
                palatte[i * 4] = (byte) i;
                palatte[i * 4 + 1] = (byte) i;
                palatte[i * 4 + 2] = (byte) i;
                palatte[i * 4 + 3] = 0;
            }
            dos.write(palatte);

            dos.write(data);
            dos.flush();
            buffer = baos.toByteArray();
            dos.close();
            baos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return buffer;
    }

}

