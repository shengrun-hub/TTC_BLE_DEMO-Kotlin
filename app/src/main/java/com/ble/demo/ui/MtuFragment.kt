package com.ble.demo.ui

import android.bluetooth.BluetoothGatt
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ble.api.DataUtil
import com.ble.demo.R
import com.ble.demo.util.BtUtil
import com.ble.utils.ToastUtil
import com.ttcble.leui.LeProxy
import kotlinx.android.synthetic.main.fragment_mtu.*

/**
 * 大数据传输，即突破一次只能发送20字节的限制，须手机系统不低于Android5.0
 * Created by Administrator on 2017/8/10 0010.
 */
class MtuFragment : Fragment(), View.OnClickListener {

    private var mSelectedAddress = ""
    private var isHex = true
    private var mDeviceAdapter: ArrayAdapter<String?>? = null
    private var mMtu = 23

    private val mGattReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)
            if (BtUtil.ACTION_DATA_AVAILABLE == intent.action) {
                if (address == mSelectedAddress) {
                    appendRxData(intent)
                }
            } else if (BtUtil.ACTION_MTU_CHANGED == intent.action) {
                val status = intent.getIntExtra(BtUtil.EXTRA_STATUS, BluetoothGatt.GATT_FAILURE)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mMtu = intent.getIntExtra(BtUtil.EXTRA_MTU, 23)
                    updateTxData()
                    ToastUtil.show(activity, "MTU has been $mMtu")
                } else {
                    ToastUtil.show(activity, "MTU update error: $status")
                }
            }
        }
    }

    private fun appendRxData(intent: Intent) {
        if (mtuLogView == null) return

        val uuid = intent.getStringExtra(BtUtil.EXTRA_UUID)
        val data = intent.getByteArrayExtra(BtUtil.EXTRA_DATA)

        val sb = StringBuilder()
            .append("uuid: $uuid")
            .append("\nlength: ${data?.size ?: 0}")
            .append("\n")

        sb.append(
            if (isHex) {
                "data: " + DataUtil.byteArrayToHex(data)
            } else {
                if (data == null) {
                    "data: "
                } else {
                    "data: " + String(data)
                }
            }
        )
        mtuLogView.append(Log.DEBUG, sb.toString())
    }

    private fun makeFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(BtUtil.ACTION_MTU_CHANGED)
        filter.addAction(BtUtil.ACTION_DATA_AVAILABLE)
        return filter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalBroadcastManager.getInstance(activity!!).registerReceiver(mGattReceiver, makeFilter())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mtu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateConnectedDevices()

        mtuBoxHex.isChecked = isHex
        mtuBoxHex.setOnClickListener {
            isHex = mtuBoxHex.isChecked
            updateTxData()
        }

        box_encrypt.setOnCheckedChangeListener { _, isChecked ->
            BtUtil.instance.setEncrypt(isChecked)
            updateTxData()
        }
        btn_send.setOnClickListener(this)
        btn_update_mtu.setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()")
        BtUtil.instance.setEncrypt(box_encrypt.isChecked)
    }

    private fun updateTxData() {
        val sb = StringBuilder()
        val max = if (box_encrypt.isChecked) mMtu - 6 else mMtu - 3 //加密要用去3字节
        if (isHex) { //hex
            for (i in 0 until max) {
                sb.append(String.format("%02X", i))
            }
        } else { //ascii
            for (i in 0 until max) {
                sb.append((i % 26 + 'A'.toInt()).toChar())
            }
        }
        edt_tx_data?.setText(sb.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(activity!!).unregisterReceiver(mGattReceiver)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_send -> send()
            R.id.btn_update_mtu -> updateMtu()
        }
    }


    /**
     * 请求更新MTU，会触发onMtuChanged()回调，如果请求成功，则APP一次最多可以发送MTU-3字节的数据，
     * 如默认MTU为23，APP一次最多可以发送20字节的数据
     *
     * 注：更新MTU要求手机系统版本不低于Android5.0
     */
    private fun updateMtu() {
        val mtuStr = edt_mtu.text.toString()
        if (mtuStr.isNotEmpty()) {
            val mtu = Integer.valueOf(mtuStr)
            Log.i(TAG, "updateMtu() - $mSelectedAddress, mtu=$mtu")
            LeProxy.instance.requestMtu(mSelectedAddress, mtu)
        }
    }

    private fun send() {
        try {
            val txData = edt_tx_data.text.toString()
            if (txData.isNotEmpty()) {
                val data = if (isHex) { //hex
                    DataUtil.hexToByteArray(txData)
                } else { //ascii
                    txData.toByteArray()
                }
                BtUtil.instance.send(mSelectedAddress, data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateConnectedDevices() {
        val deviceList = BtUtil.instance.connectedDevices
        val deviceArr: Array<String?>
        if (deviceList.isNotEmpty()) {
            deviceArr = arrayOfNulls(deviceList.size)
            for (i in deviceArr.indices) {
                deviceArr[i] = deviceList[i].address
            }
        } else {
            deviceArr = arrayOf(getString(R.string.mtu_no_device_connected))
        }
        mDeviceAdapter = ArrayAdapter(
            activity, android.R.layout.simple_spinner_dropdown_item, deviceArr
        )
        spinner_device?.adapter = mDeviceAdapter
        spinner_device?.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                mSelectedAddress = mDeviceAdapter!!.getItem(position)!!
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    companion object {
        private const val TAG = "MtuFragment"
    }
}