package app.attestation.auditor

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.widget.ImageView
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import androidx.lifecycle.MutableLiveData
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import java.io.ByteArrayOutputStream
import kotlin.math.min

class QRCodeImageAnalyzer(private val scannerOverlay: QROverlay, private val  cropPercentData : MutableLiveData<Int>, private val listener: (qrCode: String?) -> Unit): Analyzer {
    private val TAG = "QRCodeImageAnalyzer"

    private var frameCounter = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private val reader = MultiFormatReader()

    init {
        val supportedHints: MutableMap<DecodeHintType, Any> = EnumMap(
                DecodeHintType::class.java
        )
        supportedHints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        reader.setHints(supportedHints)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val proxyImage = image.image!!

       /* val rotation = image.imageInfo.rotationDegrees
        val rect = (scannerOverlay.context as QRScannerActivity).rect
        val cropPercent = (scannerOverlay.squareSize() * 100) /  min(rect.width(), rect.height())

        val frame =(scannerOverlay.context as QRScannerActivity).findViewById<PreviewView>(R.id.content_frame)

        val fWidth = frame.width
        val fHeight = frame.height

        val width  = rect.width()
        val height  = rect.height()
        val rLeft = rect.left*/

        cropPercentData.value?.let { cropPercent ->

            //preview have same image format
            val data: ByteArray = convertYuv420toNV21(proxyImage)
            //build an actual Yuv Image
            val yuvImage = YuvImage(data, ImageFormat.NV21, image.width, image.height, null)

            val croppedSize = ((min(yuvImage.width, yuvImage.height) * cropPercent) / 100)

            val left = (yuvImage.width - croppedSize) / 2
            val top = (yuvImage.height - croppedSize) / 2
            val bottom = (yuvImage.height + croppedSize) / 2
            val right = (yuvImage.width + croppedSize) / 2

            val cropRect = Rect(
                    left,
                    top,
                    right,
                    bottom
            )

            println("Cropping $cropPercent % size : $croppedSize yuvImage width : ${yuvImage.width} height ${yuvImage.height} ")

            //create a bitmap so we can crop it
            val stream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(cropRect, 100, stream)
            val rcBitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())
            stream.close()


            val activity = (scannerOverlay.context as QRScannerActivity)
            activity.runOnUiThread {
                activity.findViewById<ImageView>(R.id.output).setImageBitmap(null)
                activity.findViewById<ImageView>(R.id.output).setImageBitmap(rcBitmap)

            }

            try {
                rcBitmap?.let { _ ->
                    val intArray = IntArray(rcBitmap.width * rcBitmap.height)
                    rcBitmap.getPixels(intArray, 0, rcBitmap.width, 0, 0, rcBitmap.width, rcBitmap.height)

                    val binaryBitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(rcBitmap.width, rcBitmap.height, intArray)))
                    val result = reader.decode(binaryBitmap)
                    listener.invoke(result?.text)
                }
            } catch (ignored: NotFoundException) {
            } catch (ignored: FormatException) {
            } catch (ignored: ChecksumException) {
            } finally {
                reader.reset()
            }

            // Compute the FPS of the entire pipeline
            val frameCount = 10
            if (++frameCounter % frameCount == 0) {
                frameCounter = 0
                val now = System.currentTimeMillis()
                val delta = now - lastFpsTimestamp
                val fps = 1000 * frameCount.toFloat() / delta
                Log.d(TAG, "Analysis FPS: ${"%.02f".format(fps)}")
                lastFpsTimestamp = now
            }
        }

        //rcBitmap.recycle()
        image.close()
    }

    // this function basically takes rotation and convert them into Rect
    private fun Image.getCropRectAccordingToRotation(rotation: Int): Rect {
        return if (rotation == 0 || rotation == 180){
            val overlaySize = scannerOverlay.size
            val overlayWidth = overlaySize.width
            val overlayHeight = overlaySize.height
            val previewHeight = overlayWidth / (this.width.toFloat() / this.height)
            val heightDeltaTop = (previewHeight - overlayHeight) / 2

            val overlayRect = scannerOverlay.scanRect
            val rectStartX = overlayRect.left
            val rectStartY = heightDeltaTop + overlayRect.top

            val relativePosX: Float = rectStartX / overlayWidth
            val relativePosY: Float = rectStartY / previewHeight
            val relativeWidth: Float = overlayRect.width() / overlayWidth
            val relativeHeight: Float = overlayRect.height() / previewHeight

            if(rotation == 0 ){
                val startX = (relativePosX * this.width).toInt()
                val numberPixelW = (relativeWidth * this.width).toInt()
                val startY = (relativePosY * this.height).toInt()
                val numberPixelH = (relativeHeight * this.height).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }else {
                val numberPixelW = (relativeWidth * this.width).toInt()
                val startX = (this.width - relativePosX * this.width - numberPixelW).toInt()
                val numberPixelH = (relativeHeight * this.height).toInt()
                val startY = (height - relativePosY * this.height - numberPixelH).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }

        }else if (rotation == 90 || rotation == 270){
            val overlaySize = scannerOverlay.size
            val overlayWidth = overlaySize.width
            val overlayHeight = overlaySize.height
            val previewWidth = overlayHeight / (this.width.toFloat() / this.height)
            val widthDeltaLeft = (previewWidth - overlayWidth) / 2

            val overlayRect = scannerOverlay.scanRect
            val rectStartX = widthDeltaLeft + overlayRect.left
            val rectStartY = overlayRect.top

            val relativePosX: Float = rectStartX / previewWidth
            val relativePosY: Float = rectStartY / overlayHeight
            val relativeWidth: Float = overlayRect.width() / previewWidth
            val relativeHeight: Float = overlayRect.height() / overlayHeight

            if(rotation == 90){
                val startX = (relativePosY * this.width).toInt()
                val numberPixelW = (relativeHeight * this.width).toInt()
                val numberPixelH = (relativeWidth * this.height).toInt()
                val startY = height - (relativePosX * this.height).toInt() - numberPixelH
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }else {
                val numberPixelW = (relativeHeight * this.width).toInt()
                val numberPixelH = (relativeWidth * this.height).toInt()
                val startX = (this.width - relativePosY * this.width - numberPixelW).toInt()
                val startY = (relativePosX * this.height).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }

        }else {
            throw IllegalArgumentException("Rotation degree ($rotation) not supported!")
        }

    }

    private fun convertYuv420toNV21(image: Image): ByteArray {
        val crop = image.cropRect
        val format = image.format
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        var channelOffset = 0
        var outputStride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> {
                    channelOffset = width * height + 1
                    outputStride = 2
                }
                2 -> {
                    channelOffset = width * height
                    outputStride = 2
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride
            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                var length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer[data, channelOffset, length]
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer[rowData, 0, length]
                    for (col in 0 until w) {
                        data[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
        return data
    }

}
