package app.attestation.auditor

import android.util.Log
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import androidx.lifecycle.MutableLiveData
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.ReaderException
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import kotlin.math.min
import kotlin.math.roundToInt

class QRCodeImageAnalyzer(private val  cropPercentData : MutableLiveData<Int>, private val listener: (qrCode: String?) -> Unit): Analyzer {

    private val TAG = "QRCodeImageAnalyzer"

    private var frameCounter = 0
    private var lastFpsTimestamp = System.nanoTime()

    private val reader = MultiFormatReader()
    private var imageData = ByteArray(0)

    init {
        val supportedHints: MutableMap<DecodeHintType, Any> = EnumMap(
            DecodeHintType::class.java
        )
        supportedHints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        reader.setHints(supportedHints)
    }

    override fun analyze(image: ImageProxy) {

        val cropPercent = cropPercentData.value
        if(cropPercent == null){
            image.close()
            return
        }

        val plane = image.planes[0]
        val byteBuffer = plane.buffer

        if (imageData.size != byteBuffer.capacity()) {
            imageData = ByteArray(byteBuffer.capacity())
        }
        byteBuffer[imageData]


        val croppedSize = (min(image.width, image.height) * cropPercent) / 100
        val left = (image.width - croppedSize) / 2
        val top = (image.height - croppedSize) / 2

        val source = PlanarYUVLuminanceSource(
            imageData,
            image.width, image.height,
            left, top,
            croppedSize, croppedSize,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = reader.decodeWithState(binaryBitmap)
            listener.invoke(result.text)
        } catch (e: ReaderException) {
        } finally {
            reader.reset()
        }

        // Compute the FPS of the entire pipeline
        val frameCount = 10
        if (++frameCounter % frameCount == 0) {
            frameCounter = 0
            val now = System.nanoTime()
            val delta = now - lastFpsTimestamp
            val fps = 1_000_000_000 * frameCount.toFloat() / delta
            Log.d(TAG, "Analysis FPS: ${"%.02f".format(fps)}")
            lastFpsTimestamp = now
        }

        image.close()
    }
}
