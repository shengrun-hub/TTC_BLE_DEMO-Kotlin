package com.ble.demo.util

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ble.api.DataUtil
import com.ble.ble.BleCallBack
import com.ble.ble.BleService
import com.ble.gatt.GattAttributes
import com.ttcble.leui.LeProxy
import java.util.*

/**
 * Created by JiaJiefei on 2017/2/17.
 */
class BtUtil private constructor() {
    private var mBleService: BleService? = null
    private var mEncrypt = false

    fun setBleService(binder: IBinder) {
        // 初始化蓝牙服务
        LeProxy.instance.setService(binder)
        // 添加蓝牙事件监听
        LeProxy.instance.addCallback(mBleCallBack)

        //获取BleService实例，Demo的部分功能LeProxy没有封装
        mBleService = LeProxy.instance.getService()

        //mBleService?.maxConnectedNumber = 4 //设置最大可连接从机数量，默认为4
        //mBleService?.connectTimeout = 5000 //设置APP端的连接超时时间（单位ms）
    }

    /**
     * 设置是否加/解密接收的数据（仅限于默认的接收通道【0x1002】，依据模透传块数据是否加密而定）
     */
    fun setEncrypt(encrypt: Boolean) {
        mEncrypt = encrypt
        mBleService?.setDecode(encrypt)
    }


    fun isOADSupported(address: String): Boolean {
        val gatt = mBleService?.getBluetoothGatt(address)
        if (gatt != null) {
            //TI模块OAD服务
            val tiOADService = gatt.getService(GattAttributes.TI_OAD_Service)
            //奉加微模块OTA服务
            val phyOTAServiceUuid = UUID.fromString("5833ff01-9b8b-5191-6142-22a4536ef123")
            val phyOADService = gatt.getService(phyOTAServiceUuid)
            return tiOADService != null || phyOADService != null
        }
        return false
    }

    /**
     * 发送数据
     */
    fun send(address: String, value: ByteArray) {
        mBleService?.send(address, value, mEncrypt)
    }

    /**
     * 获取已连接的设备
     */
    val connectedDevices: List<BluetoothDevice>
        get() = if (mBleService != null) {
            mBleService!!.connectedDevices
        } else ArrayList()


    /**
     * BleCallBack集合了所有的蓝牙交互事件
     * 注意事项：回调方法所在线程不能有阻塞操作，否则可能导致数据发送失败或者某些方法无法正常回调！！！
     */
    private val mBleCallBack: BleCallBack = object : BleCallBack() {

        override fun onConnected(address: String) {
            //todo !!!这里只代表手机与模组建立了物理连接，APP还不能与模组进行数据交互
            updateBroadcast(address, ACTION_GATT_CONNECTED)
        }

        override fun onConnectTimeout(address: String) {
            updateBroadcast(address, ACTION_CONNECT_TIMEOUT)
        }

        override fun onConnectionError(address: String, error: Int, newState: Int) {
            updateBroadcast(address, ACTION_CONNECT_ERROR)
        }

        override fun onDisconnected(address: String) {
            updateBroadcast(address, ACTION_GATT_DISCONNECTED)
        }

        override fun onServicesDiscovered(address: String) {
            //TODO !!!检索服务成功，到这一步才可以与从机进行数据交互，有些手机可能需要延时几百毫秒才能数据交互
            // 打开默认的数据接收通道【0x1002】，这一步成功APP才能收到数据
            LeProxy.instance.enableNotification(address)
            updateBroadcast(address, ACTION_GATT_SERVICES_DISCOVERED)
        }

        override fun onCharacteristicChanged(
            address: String,
            characteristic: BluetoothGattCharacteristic
        ) {
            //TODO 收到模块发来的数据
            //Log.i(TAG, "onCharacteristicChanged() - $address, ${value2String(characteristic)}")
            updateBroadcast(address, characteristic)
        }

        override fun onCharacteristicRead(
            address: String,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //读取到模块数据
                //Log.i(TAG, "onCharacteristicRead() - $address, ${value2String(characteristic)}")
                updateBroadcast(address, characteristic)
            }
        }

        override fun onMtuChanged(address: String, mtu: Int, status: Int) {
            val intent = Intent(ACTION_MTU_CHANGED)
            intent.putExtra(EXTRA_ADDRESS, address)
            intent.putExtra(EXTRA_MTU, mtu)
            intent.putExtra(EXTRA_STATUS, status)
            sendBroadcast(intent)
        }
    }


    private fun value2String(characteristic: BluetoothGattCharacteristic): String {
        val uuid = characteristic.uuid.toString().subSequence(4, 8)
        return "0x$uuid, len=${characteristic.value.size} [${DataUtil.byteArrayToHex(characteristic.value)}]"
    }

    private fun updateBroadcast(address: String, action: String) {
        val intent = Intent(action)
        intent.putExtra(EXTRA_ADDRESS, address)
        sendBroadcast(intent)
    }

    private fun updateBroadcast(address: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(ACTION_DATA_AVAILABLE)
        intent.putExtra(EXTRA_ADDRESS, address)
        intent.putExtra(EXTRA_UUID, characteristic.uuid.toString())
        intent.putExtra(EXTRA_DATA, characteristic.value)
        sendBroadcast(intent)
    }

    private fun sendBroadcast(intent: Intent) {
        mBleService?.also {
            LocalBroadcastManager.getInstance(it).sendBroadcast(intent)
        }
    }


    companion object {
        private const val TAG = "LeProxy"

        //各蓝牙事件的广播action
        const val ACTION_CONNECT_TIMEOUT = "ACTION_CONNECT_TIMEOUT"
        const val ACTION_CONNECT_ERROR = "ACTION_CONNECT_ERROR"
        const val ACTION_GATT_CONNECTED = "ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "ACTION_DATA_AVAILABLE"
        const val ACTION_MTU_CHANGED = "ACTION_MTU_CHANGED"
        const val EXTRA_ADDRESS = "EXTRA_ADDRESS"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_UUID = "EXTRA_UUID"
        const val EXTRA_MTU = "EXTRA_MTU"
        const val EXTRA_STATUS = "EXTRA_STATUS"

        val instance = BtUtil()
    }
}