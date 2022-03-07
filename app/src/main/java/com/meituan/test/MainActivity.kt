package com.meituan.test

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus


/**
 * For users of Robust you may only to use MainActivity or SecondActivity,other classes are used for test.<br></br>
 * <br></br>
 * If you just want to use Robust ,we recommend you just focus on MainActivity SecondActivity and PatchManipulateImp.Especially three buttons in MainActivity<br></br>
 * <br></br>
 * in the MainActivity have three buttons; "SHOW TEXT " Button will change the text in the MainActivity,you can patch the show text.<br></br>
 * <br></br>
 * "PATCH" button will load the patch ,the patch path can be configured in PatchManipulateImp.<br></br>
 * <br></br>
 * "JUMP_SECOND_ACTIVITY" button will jump to the second ACTIVITY,so you can patch a Activity.<br></br>
 * <br></br>
 * Attention to this ,We recommend that one patch is just for one built apk ,because every  built apk has its unique mapping-r8.txt and resource id<br></br>
 *
 * @author mivanzhang
 */
class MainActivity : AppCompatActivity() {
    var textView: TextView? = null
    var button: Button? = null

    private lateinit var manager: SplitInstallManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        button = findViewById<View>(R.id.button) as Button
        textView = findViewById<View>(R.id.textView) as TextView
        val patch = findViewById<View>(R.id.patch) as Button
        manager = SplitInstallManagerFactory.create(this)
        //begin to patch
        patch.setOnClickListener {
            if (isGrantSDCardReadPermission) {
                runRobust()
            } else {
                requestPermission()
            }
        }
        findViewById<View>(R.id.jump_second_activity).setOnClickListener {
            val intent = Intent(this@MainActivity, SecondActivity::class.java)
            startActivity(intent)
        }
        findViewById<View>(R.id.jump_dynamic_activity).setOnClickListener {
            loadAndLaunchModule(DYNAMIC_MODULE)
        }
        button!!.setOnClickListener {
            Toast.makeText(
                this@MainActivity,
                "arrived in ",
                Toast.LENGTH_SHORT
            ).show()
//            var token = Jwt().token()
//            Log.v("TOKEN", token)
//
            val path = Jwt().writeFile()
            val key = Jwt().readFile(this, path)
            val token = Jwt().token(key)
            println("Token : ${token}")
        }
    }

    private fun loadAndLaunchModule(name: String) {
        // Skip loading if the module already is installed. Perform success action directly.
        if (manager.installedModules.contains(name)) {
            Toast.makeText(this, "Already installed", Toast.LENGTH_SHORT).show()
            onSuccessfulLoad(name, launch = true)
            return
        }

        // Create request to install a feature module by name.
        val request = SplitInstallRequest.newBuilder()
            .addModule(name)
            .build()

        // Load and install the requested feature module.
        manager.startInstall(request)
    }

    private val isGrantSDCardReadPermission: Boolean
        private get() = PermissionUtils.isGrantSDCardReadPermission(this)

    private fun requestPermission() {
        PermissionUtils.requestSDCardReadPermission(this, REQUEST_CODE_SDCARD_READ)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_SDCARD_READ -> handlePermissionResult()
            else -> {
            }
        }
    }

    private fun handlePermissionResult() {
        if (isGrantSDCardReadPermission) {
            runRobust()
        } else {
            Toast.makeText(
                this,
                "failure because without sd card read permission",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun runRobust() {
//        PatchExecutor(
//            applicationContext,
//            StabilityManipulateImp(),
//            RobustCallBackSample()
//        ).start()
    }

    override fun onResume() {
        // Listener can be registered even without directly triggering a download.
        manager.registerListener(listener)
        super.onResume()
    }

    override fun onPause() {
        // Make sure to dispose of the listener once it's no longer needed.
        manager.unregisterListener(listener)
        super.onPause()
    }

    companion object {
        private const val REQUEST_CODE_SDCARD_READ = 1
        private const val TAG = "MainActivity"
        private const val DYNAMIC_ACTIVITY = "com.example.dynamicfeature.DynamicMainActivity"
        private const val DYNAMIC_MODULE = "dynamicfeature"
    }

    /** Listener used to handle changes in state for install requests. */
    private val listener = SplitInstallStateUpdatedListener { state ->
        val multiInstall = state.moduleNames().size > 1
        val names = state.moduleNames().joinToString(" - ")
        when (state.status()) {
            SplitInstallSessionStatus.DOWNLOADING -> {
                Toast.makeText(this, "Downloading", Toast.LENGTH_SHORT).show()
            }
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                startIntentSender(state.resolutionIntent()?.intentSender, null, 0, 0, 0)
            }
            SplitInstallSessionStatus.INSTALLED -> {
                onSuccessfulLoad(names, launch = !multiInstall)
            }

            SplitInstallSessionStatus.INSTALLING -> Toast.makeText(this, "Installing", Toast.LENGTH_SHORT).show()
            SplitInstallSessionStatus.FAILED -> {
                Toast.makeText(this, "Error: ${state.errorCode()} for module ${state.moduleNames()}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onSuccessfulLoad(names: String, launch: Boolean) {
        if (launch) {
            Toast.makeText(this, "Launching ${names}", Toast.LENGTH_SHORT).show()
            Intent().setClassName(packageName, DYNAMIC_ACTIVITY)
                .also {
                    startActivity(it)
                }
        }
    }
}