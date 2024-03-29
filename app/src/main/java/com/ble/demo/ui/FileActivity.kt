package com.ble.demo.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import com.ble.ble.scan.LeScanner
import com.ble.demo.R
import com.ble.utils.ToastUtil
import java.io.File
import java.util.*

/**
 * 加载本地OAD文件
 */
class FileActivity : AppCompatActivity() {

    private val DIR =
        File(Environment.getExternalStorageDirectory().absolutePath + "/" + Environment.DIRECTORY_DOWNLOADS)

    private lateinit var mFileAdapter: ArrayAdapter<String>

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        mFileAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        mFileAdapter.setNotifyOnChange(true)

        val listView = ListView(this)
        listView.adapter = mFileAdapter
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val path = DIR.absolutePath + "/" + mFileAdapter.getItem(position)
            val intent = Intent()
            intent.putExtra(EXTRA_FILE_PATH, path)
            setResult(Activity.RESULT_OK, intent)
            finish()
            true
        }
        setContentView(listView)
    }


    override fun onResume() {
        super.onResume()
        if (checkPermission()) {
            mFileAdapter.addAll(localFiles)
        } else {
            AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage(R.string.no_read_external_storage_permission)
                .setPositiveButton(R.string.to_grant_permission) { _, _ ->
                    if (Build.VERSION.SDK_INT < 30) {
                        LeScanner.startAppDetailsActivity(this)
                    } else {
                        val uri = Uri.parse("package:$packageName")
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                uri
                            )
                        )
                    }
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    finish()
                }.show()
        }
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager()
        }
        if (Build.VERSION.SDK_INT >= 23) {
            val result = PermissionChecker.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            return result == PermissionChecker.PERMISSION_GRANTED
        }
        return true
    }

    private val localFiles: List<String>
        get() {
            val fileList = ArrayList<String>()
            if (DIR.exists()) {
                actionBar?.title = DIR.absolutePath
                val files = DIR.listFiles { _, name ->
                    val lowercaseName = name.lowercase()
                    (lowercaseName.endsWith(".bin")
                            || lowercaseName.endsWith(".hexe"))
                }
                if (files != null) {
                    for (file in files) if (!file.isDirectory) {
                        fileList.add(file.name)
                    }
                }
                if (fileList.size == 0) {
                    ToastUtil.show(this, "No OAD images available")
                }
            } else {
                ToastUtil.show(this, DIR.absolutePath + " does not exist")
            }
            return fileList
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    companion object {
        const val EXTRA_FILE_PATH = "EXTRA_FILE_PATH"
    }
}