package com.ble.demo

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ble.ble.BleService
import com.ble.ble.scan.LeScanner
import com.ble.demo.ui.ConnectedFragment
import com.ble.demo.ui.MtuFragment
import com.ble.demo.ui.ScanFragment
import com.ble.demo.util.BtUtil
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val mLocalReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)
            when (intent.action) {
                BtUtil.ACTION_GATT_CONNECTED -> showToast(R.string.scan_connected, address)
                BtUtil.ACTION_GATT_DISCONNECTED -> showToast(R.string.scan_disconnected, address)
                BtUtil.ACTION_CONNECT_ERROR -> showToast(R.string.scan_connection_error, address)
                BtUtil.ACTION_CONNECT_TIMEOUT -> showToast(R.string.scan_connect_timeout, address)
                BtUtil.ACTION_GATT_SERVICES_DISCOVERED -> showToast("Services discovered: $address")
            }
        }
    }


    private fun showToast(strId: Int, address: String) {
        showToast(getString(strId) + " " + address)
    }

    private fun showToast(str: String) {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show()
    }

    private fun makeFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(BtUtil.ACTION_GATT_CONNECTED)
        filter.addAction(BtUtil.ACTION_GATT_DISCONNECTED)
        filter.addAction(BtUtil.ACTION_CONNECT_ERROR)
        filter.addAction(BtUtil.ACTION_CONNECT_TIMEOUT)
        filter.addAction(BtUtil.ACTION_GATT_SERVICES_DISCOVERED)
        return filter
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            BtUtil.instance.setBleService(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.subtitle = appVersion

        initView()
        bindService(Intent(this, BleService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, makeFilter())
    }

    private val appVersion: String
        get() = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: Exception) {
            ""
        }

    private fun initView() {
        val fragmentTitles = resources.getStringArray(R.array.fragment_titles)
        tabhost.setup(this, supportFragmentManager, R.id.frame_content)
        for (i in fragmentTitles.indices) {
            val ts = tabhost.newTabSpec(fragmentTitles[i])
            ts.setIndicator(fragmentTitles[i])
            when (i) {
                FRAGMENT_SCAN -> tabhost.addTab(
                    ts,
                    ScanFragment::class.java,
                    null
                )
                FRAGMENT_CONNECTED -> tabhost.addTab(
                    ts,
                    ConnectedFragment::class.java,
                    null
                )
                FRAGMENT_MTU -> tabhost.addTab(
                    ts,
                    MtuFragment::class.java,
                    null
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled) {
            //申请打开手机蓝牙，requestCode为LeScanner.REQUEST_ENABLE_BT
            LeScanner.requestEnableBluetooth(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == LeScanner.REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver)
        unbindService(mConnection)
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val FRAGMENT_SCAN = 0
        private const val FRAGMENT_CONNECTED = 1
        private const val FRAGMENT_MTU = 2

        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
    }
}