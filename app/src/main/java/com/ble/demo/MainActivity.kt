package com.ble.demo

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ble.ble.BleService
import com.ble.ble.scan.LeScanner
import com.ble.demo.databinding.ActivityMainBinding
import com.ble.demo.ui.BaseActivity
import com.ble.demo.ui.ScanFragment
import com.ble.demo.util.BtUtil

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private var mIsResumed = false

    private val mLocalReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (!mIsResumed) return
            val address = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)!!
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
        override fun onServiceDisconnected(name: ComponentName) {}
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.subtitle = appVersion

        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_content, ScanFragment())
            .commit()

        bindService(Intent(this, BleService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, makeFilter())
    }

    private val appVersion: String?
        get() = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.versionName
        } catch (_: Exception) { "" }

    override fun onResume() {
        super.onResume()
        mIsResumed = true
    }

    override fun onPause() {
        super.onPause()
        mIsResumed = false
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
}
