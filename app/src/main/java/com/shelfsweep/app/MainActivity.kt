package com.shelfsweep.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // keep the screen awake while walking the aisles
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vm.cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.cameraGranted = granted
        }
        if (!vm.cameraGranted) askCamera.launch(Manifest.permission.CAMERA)

        setContent {
            AppTheme {
                ShelfSweeperApp(vm, onAskCamera = { askCamera.launch(Manifest.permission.CAMERA) })
            }
        }
    }
}
