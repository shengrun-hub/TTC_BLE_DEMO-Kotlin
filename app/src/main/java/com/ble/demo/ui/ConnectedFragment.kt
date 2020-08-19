package com.ble.demo.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ble.api.DataUtil
import com.ble.demo.LeDevice
import com.ble.demo.MainActivity
import com.ble.demo.R
import com.ble.demo.util.BtUtil
import com.ble.demo.util.HexAsciiWatcher
import com.ble.utils.DimensUtil
import com.ble.utils.TimeUtil
import com.ttcble.leui.LeProxy
import kotlinx.android.synthetic.main.fragment_connected.*
import java.util.*

class ConnectedFragment : Fragment() {

    private var mSelectedAddressList = ArrayList<String>()
    private lateinit var mDeviceListAdapter: ConnectedDeviceListAdapter
    private lateinit var mInputWatcher: HexAsciiWatcher

    private val mLocalReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)
            when (intent.action) {
                BtUtil.ACTION_GATT_DISCONNECTED -> {
                    mSelectedAddressList.remove(address)
                    mDeviceListAdapter.removeDevice(address)
                }
                BtUtil.ACTION_DATA_AVAILABLE -> displayRxData(intent)
            }
        }
    }

    private fun displayRxData(intent: Intent) {
        if (cbox_hex == null) return

        val address = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)
        val uuid = intent.getStringExtra(BtUtil.EXTRA_UUID)
        val data = intent.getByteArrayExtra(BtUtil.EXTRA_DATA)
        val device = mDeviceListAdapter.getDevice(address)
        if (device != null) {
            var dataStr = "timestamp: ${TimeUtil.timestamp("MM-dd HH:mm:ss.SSS")}\n" +
                    "uuid: $uuid\n" +
                    "length: ${data?.size ?: 0}\n"

            dataStr += if (cbox_hex.isChecked) {
                "data: ${DataUtil.byteArrayToHex(data)}"
            } else {
                if (data == null) {
                    "data: "
                } else {
                    "data: " + String(data)
                }
            }
            device.rxData = dataStr
            mDeviceListAdapter.notifyDataSetChanged()
        }
    }

    private fun makeFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(BtUtil.ACTION_GATT_DISCONNECTED)
        filter.addAction(BtUtil.ACTION_DATA_AVAILABLE)
        return filter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDeviceListAdapter = ConnectedDeviceListAdapter()
        LocalBroadcastManager.getInstance(activity!!).registerReceiver(mLocalReceiver, makeFilter())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_connected, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        listView1.layoutManager = LinearLayoutManager(activity)
        listView1.adapter = mDeviceListAdapter
        mInputWatcher = HexAsciiWatcher(activity)
        mInputWatcher.setHost(edt_msg)
        mInputWatcher.setIndicator(tv_input_bytes)
        edt_msg.addTextChangedListener(mInputWatcher)
        updateEditText(false)
        cbox_encrypt.setOnCheckedChangeListener { _, isChecked ->
            updateEditText(false)
            BtUtil.instance.setEncrypt(isChecked)
        }
        cbox_hex.setOnCheckedChangeListener { _, _ ->
            updateEditText(true)
        }
        btn_send.setOnClickListener {
            send()
        }
        edt_msg.setOnClickListener {
            goHexInputActivity()
        }
        edt_msg.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            Log.e(TAG, "onFocusChange() - hasFocus=$hasFocus")
            if (hasFocus) goHexInputActivity()
        }
    }


    private fun goHexInputActivity() {
        if (cbox_hex.isChecked) {
            val intent = Intent(activity, HexInputActivity::class.java)
            intent.putExtra(HexInputActivity.EXTRA_MAX_LENGTH, mInputWatcher.maxLength)
            intent.putExtra(HexInputActivity.EXTRA_HEX_STRING, edt_msg.text.toString())
            startActivityForResult(intent, REQ_HEX_INPUT)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQ_HEX_INPUT && resultCode == Activity.RESULT_OK) {
            val hexStr = data!!.getStringExtra(HexInputActivity.EXTRA_HEX_STRING)
            edt_msg?.setText(hexStr)
            edt_msg?.setSelection(hexStr.length)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // 向勾选的设备发送数据
    private fun send() {
        var inputStr = edt_msg.text.toString()
        if (inputStr.isNotEmpty()) {
            val data: ByteArray
            if (cbox_hex.isChecked) {
                data = DataUtil.hexToByteArray(inputStr)
            } else {
                // 这里将换行符替换成Windows系统的，不过这样统计的字节数就会偏少
                inputStr = inputStr.replace("\r\n".toRegex(), "\n")
                inputStr = inputStr.replace("\n".toRegex(), "\r\n")
                data = inputStr.toByteArray()
            }
            Log.e(TAG, inputStr + " -> " + DataUtil.byteArrayToHex(data))
            for (i in mSelectedAddressList.indices) {
                BtUtil.instance.send(mSelectedAddressList[i], data)
            }
        }
    }

    private fun updateEditText(clearText: Boolean) {
        mInputWatcher.setTextType(if (cbox_hex.isChecked) HexAsciiWatcher.HEX else HexAsciiWatcher.ASCII)
        val maxLen: Int //可输入的字符串长度
        val hintText: String
        if (cbox_hex.isChecked) {
            maxLen = if (cbox_encrypt.isChecked) {
                34
            } else {
                40
            }
            hintText = getString(R.string.connected_send_hex_hint, maxLen / 2)
        } else {
            maxLen = if (cbox_encrypt.isChecked) {
                17
            } else {
                20
            }
            hintText = getString(R.string.connected_send_ascii_hint, maxLen)
        }
        mInputWatcher.maxLength = maxLen
        edt_msg.hint = hintText
        if (clearText) {
            edt_msg.setText("")
            mInputWatcher.setIndicatorText(getString(R.string.input_bytes, 0))
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()")
        BtUtil.instance.setEncrypt(cbox_encrypt.isChecked)
        mSelectedAddressList.clear()
        mDeviceListAdapter.clear()
        val connectedDevices = BtUtil.instance.connectedDevices
        for (i in connectedDevices.indices) {
            val name = connectedDevices[i].name
            val address = connectedDevices[i].address
            mSelectedAddressList.add(address)
            mDeviceListAdapter.addDevice(LeDevice(name, address))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(activity!!).unregisterReceiver(mLocalReceiver)
    }


    private inner class ConnectedDeviceListAdapter : RecyclerView.Adapter<ViewHolder>() {
        val mDevices = ArrayList<LeDevice>()

        fun getDevice(address: String): LeDevice? {
            for (connectedLeDevice in mDevices) {
                if (connectedLeDevice.address == address) {
                    return connectedLeDevice
                }
            }
            return null
        }

        fun addDevice(device: LeDevice) {
            if (!mDevices.contains(device)) {
                device.isOadSupported = BtUtil.instance.isOADSupported(device.address)
                mDevices.add(device)
                notifyDataSetChanged()
            }
        }

        fun removeDevice(address: String) {
            var location = -1
            for (i in mDevices.indices) {
                if (mDevices[i].address == address) {
                    location = i
                    break
                }
            }
            if (location != -1) {
                mDevices.removeAt(location)
                notifyDataSetChanged()
            }
        }

        fun clear() {
            mDevices.clear()
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int {
            return mDevices.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val view = LayoutInflater.from(context).inflate(R.layout.item_device_list, null)
            val width = DimensUtil.getScreenWidth(context)
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            view.layoutParams = ViewGroup.LayoutParams(width, height)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = mDevices[position]
            holder.imageView.visibility = View.GONE
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.disconnect.setOnClickListener { LeProxy.instance.disconnect(device.address) }
            if (TextUtils.isEmpty(device.name)) {
                holder.tvName.setText(R.string.unknown_device)
            } else {
                holder.tvName.text = device.name
            }
            holder.tvAddress.text = device.address
            holder.checkBox.visibility = View.VISIBLE
            holder.tvRxData.visibility = View.VISIBLE
            holder.tvRxData.text = device.rxData
            val address = device.address
            holder.checkBox.isChecked = mSelectedAddressList.contains(address)
            holder.checkBox.setOnCheckedChangeListener { _, isChecked -> // 勾选设备，发送数据时只给已勾选的设备发送
                if (isChecked) {
                    if (!mSelectedAddressList.contains(address)) {
                        mSelectedAddressList.add(address)
                    }
                } else {
                    if (mSelectedAddressList.contains(address)) {
                        mSelectedAddressList.remove(address)
                    }
                }
                Log.i(TAG, "Selected " + mSelectedAddressList.size)
            }
            if (device.isOadSupported) {
                holder.btnOAD.visibility = View.VISIBLE
            } else {
                holder.btnOAD.visibility = View.GONE
            }

            holder.btnOAD.setOnClickListener { // 进入OAD界面
                val oadIntent = Intent(activity, OADActivity::class.java)
                oadIntent.putExtra(MainActivity.EXTRA_DEVICE_NAME, device.name)
                oadIntent.putExtra(MainActivity.EXTRA_DEVICE_ADDRESS, device.address)
                startActivity(oadIntent)
            }
        }
    }

    private class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView
        val tvAddress: TextView
        val tvRxData: TextView
        val checkBox: CheckBox
        val btnOAD: TextView
        val disconnect: TextView
        val imageView: ImageView

        init {
            tvName = view.findViewById(R.id.device_name)
            tvAddress = view.findViewById(R.id.device_address)
            tvRxData = view.findViewById(R.id.txt_rx_data)
            checkBox = view.findViewById(R.id.checkBox1)
            btnOAD = view.findViewById(R.id.btn_oad)
            imageView = view.findViewById(R.id.imageView1)
            disconnect = view.findViewById(R.id.btn_connect)
            disconnect.setText(R.string.menu_disconnect)
        }
    }

    companion object {
        private const val TAG = "ConnectedFragment"

        private const val REQ_HEX_INPUT = 3
    }
}