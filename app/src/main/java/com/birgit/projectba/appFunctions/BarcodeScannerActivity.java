package com.birgit.projectba.appFunctions;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Network;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import com.birgit.projectba.MainActivity;
import com.birgit.projectba.R;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.android.gms.internal.zzhu.runOnUiThread;

public class BarcodeScannerActivity extends AppCompatActivity
{
    private final String TAG = BarcodeScannerActivity.class.getSimpleName();

    // display the camera preview
    private TextureView textureView;

    private byte[] image;
    private Size imageSize;

    // camera
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    Handler backgroundHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scanner);
        textureView = (TextureView) findViewById(R.id.texture_view);

        // set a listener to the texture view to receive event when it is set up
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener()
        {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
            {
                // view is set up; now camera con open
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
            {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
            {
                if(cameraDevice != null)
                    closeCamera();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface)
            {

            }
        });

        // set a touch listener to the view to record interactions to take a picture
        textureView.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {

                if(event.getAction() == MotionEvent.ACTION_UP)
                {
                    doScanning();
                }
                return true;
            }
        });
    }


    // callback #1
    // returns a camera and can open it
    private final CameraDevice.StateCallback cameraDeviceCallback = new CameraDevice.StateCallback()
    {
        // a camera device is returned --> preview can be started
        @Override
        public void onOpened(CameraDevice camera)
        {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera)
        {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error)
        {
            Toast.makeText(BarcodeScannerActivity.this, "error opening camera", Toast.LENGTH_SHORT).show();
            if(camera != null)
                cameraDevice.close();
            cameraDevice = null;
            returnToMainActivity("");
        }
    };

    // #1 opens the camera
    private void openCamera()
    {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try
        {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageSize = map.getOutputSizes(ImageFormat.JPEG)[0];

            // check if necessary permissions are granted
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(null, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            }
            // when camera is open cameraDeviceCallback is called
            manager.openCamera(cameraId, cameraDeviceCallback, null);

        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    // #2 creates the camera preview to show on the screen
    public void createCameraPreview()
    {
        try
        {
            SurfaceTexture texture = textureView.getSurfaceTexture();

            texture.setDefaultBufferSize(imageSize.getWidth(), imageSize.getHeight());
            Surface surface = new Surface(texture);

            // set a capture request for getting the image data
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback()
            {
                // camera preview is ready and running
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
                {
                    Log.d(TAG, "#6 camera configured");
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;

                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // #2 sets repeating request to create video preview
    private void updatePreview()
    {
        if(null == cameraDevice)
        {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            Log.d(TAG, "#9 request repeat preview update");
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    // #3 takes a picture and scans it for barcodes
    public void doScanning()
    {
        // check if camera open
        if (cameraDevice == null)
        {
            closeCamera();
        }
        else
        {
            // takes a new picture and gets the data to barcode scanner to scan
            takeNewPicture();
        }
    }


    // callback #4
    // called when image is aquired successfully
    private CameraCaptureSession.CaptureCallback captureSessionCallback = new CameraCaptureSession.CaptureCallback()
    {
        // the request is finished --> activity can return to main
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)
        {
            closeCamera();
        }
    };

    // #4 aquires jpeg-image
    private synchronized void takeNewPicture()
    {
         try
        {
            // set image size
            int width = imageSize.getWidth();
            int height = imageSize.getHeight();

            // use an image reader to get the data
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());

            // add fake view -> otherwise not working
            TextureView textureViewF = new TextureView(this);
            SurfaceTexture surfaceTexture = new SurfaceTexture(textureViewF.getId());
            textureViewF.setSurfaceTexture(surfaceTexture);
            outputSurfaces.add(new Surface(textureViewF.getSurfaceTexture()));

            // create a capture request to take a picture
            final CaptureRequest.Builder captureBuilder;

            // add settings to the capture request: set autofocus
            captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, Surface.ROTATION_0);

            // callback: image is available
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener()
            {
                @Override
                public void onImageAvailable(ImageReader reader)
                {
                    Image imageData = null;
                    try
                    {
                        // get the image data
                        imageData = reader.acquireLatestImage();
                        ByteBuffer buffer = imageData.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        image = bytes;
                        // now image can be scanned for barcodes
                        scanBarCode();
                    }
                    finally
                    {
                        if (imageData != null)
                        {
                            imageData.close();
                        }
                    }
                }

            }, backgroundHandler);

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback()
            {
                @Override
                public void onConfigured(CameraCaptureSession session)
                {
                    try
                    {
                        synchronized (this)
                        {
                            session.capture(captureBuilder.build(), captureSessionCallback, backgroundHandler);
                        }
                    }
                    catch (CameraAccessException e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session)
                {
                }
            }, backgroundHandler);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }


    // #5 scans image for barcodes
    public void scanBarCode()
    {
        // convert the aquired jpeg image to a bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);

        // set a the detector
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(this.getApplicationContext()).build();
        // create a frame with the bitmap and set it to barcodeDetector to read the barcode
        Frame frame = new Frame.Builder().setBitmap(bitmap).build();
        SparseArray<Barcode> barcodes = barcodeDetector.detect(frame);
        Barcode barcode = null;
        String barcodeValue = "";

        // the phone does not have the necessary setup for the barcode detection
        if (!barcodeDetector.isOperational())
        {
            Log.d(TAG, "no setup barcode");
        }

        // a barcode was detected
        if (barcodes.size() > 0)
        {
            barcode = barcodes.valueAt(0);
            barcodeValue = barcode.rawValue;
            // notify the user about it
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(BarcodeScannerActivity.this, "bar code detected", Toast.LENGTH_LONG).show();
                }
            });
        }
        else
        {
            // notify the user that no barcode was found
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(BarcodeScannerActivity.this, " no bar code", Toast.LENGTH_LONG).show();
                }
            });
        }

        closeCamera();

        // barcode scanning finished -> return back
        returnToMainActivity(barcodeValue);
        if(cameraDevice == null)
        {
            textureView.setOnTouchListener(null);
        }
    }

    private void closeCamera()
    {
        if (null != cameraDevice)
        {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    // closes this activity and returns with result to main
    private void returnToMainActivity(String barcode)
    {
        // craete an intent to go back to main activity
        Intent intent = new Intent();

        // if there was a barcode detected; put it into intent to main
        if(barcode.length() > 1)
            intent.putExtra("code", barcode);

        // finish activity
        setResult(1, intent);
        finish();
    }

}
