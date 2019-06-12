var
	exec = require('cordova/exec'),
	channel = require('cordova/channel'),
	CDVNetEvent = require('cordova-plugin-networking-bluetooth.CDVNetEvent')
;

exports.getListDevice = function (arg0, success, error) {
    exec(success, error, 'FingerPrintPlugin', 'getListDevice', [arg0]);
};

exports.doEnroll = function (arg0, success, error) {
    exec(success, error, 'FingerPrintPlugin', 'doEnroll', [arg0]);
};

exports.startDiscovery = function (success, error) {
    exec(success, error, 'FingerPrintPlugin', 'startDiscovery', []);
};

exports.stopDiscovery = function (success, error) {
    exec(success, error, 'FingerPrintPlugin', 'stopDiscovery', []);
};

exports.getDevices = function (success, error) {
	exec(success, error, 'FingerPrintPlugin', 'getDevices', []);
};

exports.connect = function (address, uuid, success, error) {
	exec(success, error, 'FingerPrintPlugin', 'connect', [address, uuid]);
};

exports.send = function (socketId, data, success, error) {
	exec(success, error, 'FingerPrintPlugin', 'send', [socketId, data]);
};

exports.listenUsingRfcomm = function (uuid, success, error) {
	exec(success, error, 'FingerPrintPlugin', 'listenUsingRfcomm', [uuid]);
};

// Events
exports.onAdapterStateChanged = Object.create(CDVNetEvent);
exports.onAdapterStateChanged.init();

exports.onDeviceAdded = Object.create(CDVNetEvent);
exports.onDeviceAdded.init();

exports.onReceive = Object.create(CDVNetEvent);
exports.onReceive.init();

exports.onReceiveError = Object.create(CDVNetEvent);
exports.onReceiveError.init();

exports.onAccept = Object.create(CDVNetEvent);
exports.onAccept.init();

exports.onAcceptError = Object.create(CDVNetEvent);
exports.onAcceptError.init();

channel.onCordovaReady.subscribe(function() {
	exec(function (adapterState) {
		exports.onAdapterStateChanged.fire(adapterState);
	}, null, 'FingerPrintPlugin', 'registerAdapterStateChanged', []);

	exec(function (deviceInfo) {
		exports.onDeviceAdded.fire(deviceInfo);
	}, null, 'FingerPrintPlugin', 'registerDeviceAdded', []);

	exec(function (socketId, data) {
		exports.onReceive.fire({
			socketId: socketId,
			data: data
		});
	}, null, 'FingerPrintPlugin', 'registerReceive', []);

	exec(function (info) {
		exports.onReceiveError.fire(info);
	}, null, 'FingerPrintPlugin', 'registerReceiveError', []);

	exec(function (serverSocketId, clientSocketId) {
		exports.onAccept.fire({
			socketId: serverSocketId,
			clientSocketId: clientSocketId
		});
	}, null, 'FingerPrintPlugin', 'registerAccept', []);

	exec(function (info) {
		exports.onAcceptError.fire(info);
	}, null, 'FingerPrintPlugin', 'registerAcceptError', []);
});