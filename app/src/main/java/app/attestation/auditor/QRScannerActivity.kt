package app.attestation.auditor

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.MutableLiveData
import kotlin.math.min

class QRScannerActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var overlayView: QROverlay
    private lateinit var camera: Camera
    private lateinit var contentFrame: PreviewView

    private val handler = Handler(Looper.getMainLooper())
    private val autoCenterFocusDuration = 10000L

    private val cropPercent = MutableLiveData<Int>()

    private val runnable = Runnable {
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            contentFrame.width.toFloat(), contentFrame.height.toFloat()
        )

        val autoFocusPoint = factory.createPoint(contentFrame.width / 2.0f,
            contentFrame.height / 2.0f, overlayView.size.width.toFloat())

        camera.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(autoFocusPoint).disableAutoCancel().build()
        )

        startTimer()
    }

    private fun startTimer() {
        handler.postDelayed(runnable, autoCenterFocusDuration)
    }

    private fun cancelTimer() {
        handler.removeCallbacks(runnable)
    }

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_qrscanner)
        startCamera()
    }

    override fun onResume() {
        super.onResume()
        startTimer()
    }

    override fun onPause() {
        super.onPause()
        cancelTimer()
    }

    public override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    fun getOverlayView(): QROverlay {
        return overlayView
    }

    private fun startCamera() {
        contentFrame = findViewById(R.id.content_frame)
        contentFrame.setScaleType(PreviewView.ScaleType.FIT_CENTER)
        //adding scaling can cause problem with analizer unless it also handles scaling
        // currently it only handle rotation to keep it simple
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = cameraSelector
        cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)

        cameraProviderFuture.addListener(
            {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(SurfaceProviderWithCallback(contentFrame){

                        })
                    }

                preview.setSurfaceProvider(SurfaceProviderWithCallback(contentFrame){
                    var rect = preview.resolutionInfo?.cropRect!!
                    this.cropPercent.postValue((overlayView.squareSize() * 100) /  min(rect.width(), rect.height()))
                })
                overlayView = findViewById(R.id.overlay)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(960, 960))
                    .build()

                imageAnalysis.setAnalyzer(
                    executor,
                    QRCodeImageAnalyzer (overlayView, cropPercent) { response ->
                        if (response != null) {
                            handleResult(response)
                        }
                    }
                )
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    inner class SurfaceProviderWithCallback (
            private val preview: PreviewView,
            private val callback : () -> Unit
    ) : Preview.SurfaceProvider {

        override fun onSurfaceRequested(request: SurfaceRequest) {
            preview.surfaceProvider.onSurfaceRequested(request)
            callback.invoke()
        }
    }


    private fun handleResult(rawResult: String) {
        val result = Intent()
        result.putExtra(EXTRA_SCAN_RESULT, rawResult)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_SCAN_RESULT = "app.attestation.auditor.SCAN_RESULT"
    }
}
