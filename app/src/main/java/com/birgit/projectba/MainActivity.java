package com.birgit.projectba;

import com.birgit.projectba.appFunctions.BarcodeScannerActivity;
import com.birgit.projectba.appFunctions.NetworkConnection;
import com.birgit.projectba.appFunctions.SaveADF;
import com.birgit.projectba.appFunctions.TouchHandler;
import com.birgit.projectba.graphicHolderClasses.MeasureLine;
import com.birgit.projectba.graphicHolderClasses.ObjectHolder;
import com.birgit.projectba.utils.MathUtils;
import com.birgit.projectba.graphicHolderClasses.TextureManager;
import com.birgit.projectba.utils.Utils;
import com.google.atap.tango.ux.TangoUx;
import com.google.atap.tango.ux.TangoUxLayout;
import com.google.atap.tango.ux.UxExceptionEvent;
import com.google.atap.tango.ux.UxExceptionEventListener;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;

import android.app.Activity;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.view.SurfaceView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import static com.birgit.projectba.utils.MathUtils.calculateProjectionMatrix;

import com.birgit.projectba.appFunctions.TouchHandler.TOUCH_MODE;
import com.birgit.projectba.appFunctions.TouchHandler.TouchHandlerCallbackIF;
import com.birgit.projectba.appFunctions.NetworkConnection.NetworkResponseCallbackIF;

// main class of the app; coordinates all other classes
public class MainActivity extends Activity
{
    private static final String TAG = MainActivity.class.getSimpleName();

    // tango
    private Tango tango;
    private TangoConfig tangoConfig;
    // stores the point cloud data
    private TangoPointCloudManager pointCloudManager;
    // for user communication
    private TangoUx tangoUx;
    private boolean isTangoConnected = false;
    private volatile TangoImageBuffer currentImage;

    // camera
    private AtomicBoolean isCameraFrameAvailable = new AtomicBoolean(false);
    // timestamp when last image was updated from camera
    private double lastCameraPoseTimestamp = 0;
    // timestamp of last pose received from tango
    private double lastTangoPoseTimestamp = 0;

    // area description
    private boolean syncADF = false;
    private boolean relocalised = false;
    private TangoPoseData relocalisedADFpose = null;

    // rendering
    private SurfaceView surfaceView;
    private ARrenderer renderer;
    private int connectedTextureID = 0;
    private int displayRotation = 0;

    // layout
    private Button menuButton;
    private PopupWindow popupWindow;
    private final View popupView = null;
    private String[] poseString = new String[2];
    private TextView positionTextView;
    private TextView orientationTextView;

    // modes
    // object recognition with barcode
    private boolean scanBarcodeFunction = false;
    // object recognition with nn
    private boolean scanImageForObjectFunction = true;
    // add measure line points
    private boolean measureFunction = false;
    // place text cube
    private boolean objectFunction = true;

    // listener interfaces
    private TouchHandler touchHandler;

    // lifecycle methods **********************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.layout_main);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        renderer = ARrenderer.initRenderer(this);
        surfaceView.setSurfaceRenderer(renderer);

        initialiseMenuLayout();

        tangoUx = initTangoUx();
        pointCloudManager = new TangoPointCloudManager();

        // init listener methods
        initDisplay();

        setupTouchFunction();
        TextureManager.initTextureManager(this);

        // needed for saving adf
        startActivityForResult(Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE), 0);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        surfaceView.onResume();

        setRenderer();
        connectTango();
    }

    @Override
    public void onPause()
    {
        super.onPause();

        surfaceView.onPause();
        disconnectTango();
    }


    // use methods ***************************************************************

    // starts the listener which receives the data from tango
    private void startTangoListener()
    {
        // coordinate frames that are needed to measure relative poses
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                // start position of current session
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                // current position of device
                TangoPoseData.COORDINATE_FRAME_DEVICE
        ));
        framePairs.add(new TangoCoordinateFramePair(
                // start position of ADF (-> start pose from loaded session)
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE
        ));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
        ));

        // connect the tango object to the listener
        tango.connectListener(framePairs, new Tango.TangoUpdateCallback() {
            @Override
            public void onPoseAvailable(TangoPoseData pose)
            {
                // give data to tangoUX object that checks whether tango is recording pose data correctly
                if (tangoUx != null)
                    tangoUx.updatePoseStatus(pose.statusCode);

                if(syncADF && !relocalised)
                {
                    Log.d(TAG, "check Relocalisation");
                    checkRelocalisation(pose);
                }

                // dont always update -> performance
                if(pose.timestamp > lastTangoPoseTimestamp+0.5)
                {
                    TangoPoseData oglpose = TangoSupport.getPoseAtTime(
                            pose.timestamp,
                            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                            TangoPoseData.COORDINATE_FRAME_DEVICE,
                            TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                            TangoSupport.ROTATION_0);

                    poseString = Utils.poseString(oglpose);

                    // has to run on ui thread --> here tango thread
                    // update pose string to show on menu
                    lastTangoPoseTimestamp = pose.timestamp;
                    runOnUiThread(
                            new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    positionTextView.setText(poseString[0]);
                                    orientationTextView.setText(poseString[1]);
                                }
                            }
                    );
                }
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                if (tangoUx != null)
                    tangoUx.updatePointCloud(pointCloud);
                // point cloud data are stored in pointCloudManager object
                pointCloudManager.updatePointCloud(pointCloud);
            }

            @Override
            public void onTangoEvent(TangoEvent event)
            {
                // send event to tangoUX -> shows information to user about it
                if (tangoUx != null)
                    tangoUx.updateTangoEvent(event);
            }

            @Override
            public void onFrameAvailable(int cameraId)
            {
                // frame from colour camera is available
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR)
                {
                    if (surfaceView.getRenderMode() != GLSurfaceView.RENDERMODE_WHEN_DIRTY)
                    {
                        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                    }
                    // inform render object to update image
                    isCameraFrameAvailable.set(true);
                    surfaceView.requestRender();
                }
            }
        });
        // methods receives also image data
        tango.experimentalConnectOnFrameListener(TangoCameraIntrinsics.TANGO_CAMERA_COLOR, new Tango.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(TangoImageBuffer tangoImageBuffer, int i)
            {
                currentImage = copyImageBuffer(tangoImageBuffer);
            }
            TangoImageBuffer copyImageBuffer(TangoImageBuffer imageBuffer)
            {
                ByteBuffer clone = ByteBuffer.allocateDirect(imageBuffer.data.capacity());
                imageBuffer.data.rewind();
                clone.put(imageBuffer.data);
                imageBuffer.data.rewind();
                clone.flip();
                TangoImageBuffer bufferReturn = new TangoImageBuffer(
                        imageBuffer.width, imageBuffer.height,
                        imageBuffer.stride, imageBuffer.frameNumber,
                        imageBuffer.timestamp, imageBuffer.format, clone
                );
                return bufferReturn;
            }
        });
    }

    // sets the render object
    private void setRenderer()
    {
        // callback method from renderer
        renderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime)
            {
                try
                {
                    synchronized (MainActivity.this)
                    {
                        if (!isTangoConnected)
                            return;

                        // if renderer has not yet a projection matrix
                        if (!renderer.isProjectionMatrixConfigured())
                        {
                            TangoCameraIntrinsics intrinsics =
                                    TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            displayRotation);
                            // set the projection matrix to the renderer
                            float[] projectionMatrix = calculateProjectionMatrix(intrinsics);
                            renderer.setProjectionMatrix(projectionMatrix);
                        }

                        // connect the tango object with the renderer background texture
                        int backgroundTextureID = renderer.getTextureId();
                        if (connectedTextureID != backgroundTextureID)
                        {
                            tango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    backgroundTextureID);
                            connectedTextureID = backgroundTextureID;
                        }

                        // update the background if new image is available
                        double cameraFrameTimestamp = 0;
                        if (isCameraFrameAvailable.compareAndSet(true, false))
                        {
                            cameraFrameTimestamp = tango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        }

                        // check if recorded image is latest and update
                        if (cameraFrameTimestamp > lastCameraPoseTimestamp)
                        {
                            // get the pose at the time of the image recorded
                            TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(
                                    cameraFrameTimestamp,
                                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    displayRotation);
                            if (lastFramePose.statusCode == TangoPoseData.POSE_VALID)
                            {
                                // sets the view to the renderer to set view matrix
                                renderer.updateViewMatrix(lastFramePose);
                                lastCameraPoseTimestamp = lastFramePose.timestamp;
                            } else
                            {
                                Log.w(TAG, "Can't get device pose at time: " +
                                        cameraFrameTimestamp);
                            }
                        }
                    }
                } catch (TangoErrorException e)
                {
                    Log.e(TAG, "Tango error in OpenGL thread", e);
                } catch (Throwable t) {
                    Log.d(TAG, "Exception in OpenGL", t);
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {
            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {
            }

            @Override
            public boolean callPreFrame() {
                return true;
            }
        });
    }


    // setup/disconnect tango methods *************************************************************

    // connects the app to the tango-service
    private void connectTango()
    {
        Log.d(TAG, "connect tanog");
        tango = new Tango(MainActivity.this, new Runnable()
        {
            @Override
            public void run()
            {
                synchronized (MainActivity.this) {
                    try
                    {
                        TangoSupport.initialize();
                        // start the tangoUX for user feedback
                        tangoUx.start(new TangoUx.StartParams());
                        // set the configuration for tango and connect it
                        tangoConfig = setupTangoConfig(tango);
                        tango.connect(tangoConfig);
                        // starts the listeners to receive data from tango
                        startTangoListener();
                        isTangoConnected = true;
                        setDisplayRotation();
                    } catch (TangoOutOfDateException e) {
                        if (tangoUx != null)
                            tangoUx.showTangoOutOfDate();
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                    }
                }
            }
        });
    }

    // disconnect from the tango-service
    private synchronized void disconnectTango()
    {
        if (isTangoConnected) {
                try
                {
                    // disconnect all tango things
                    tangoUx.stop();
                    renderer.getCurrentScene().clearFrameCallbacks();
                    tango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                    connectedTextureID = 0;
                    tango.disconnect();
                    tango = null;
                    isTangoConnected = false;
                    Log.d(TAG, "tango disconnected");
                } catch (TangoErrorException e) {
                    Log.e(TAG, getString(R.string.exception_tango_error), e);
                }
            }
    }

    // set the configuration for the tango-service
    private TangoConfig setupTangoConfig(Tango tango)
    {
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);

        // motion tracking
        config.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);

        // color and point cloud depth camera
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);

        // general settings
        //config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        //config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);

        // area learning
        if(syncADF)
        {
            Log.d(TAG, "synchADF");
            ArrayList<String> adfList = tango.listAreaDescriptions();
            Log.d(TAG, "#adf: "+adfList.size());
            if (adfList.size() > 0)
            {
                // gets the latest adf file: default
                config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, adfList.get(adfList.size() - 1));
            }
        }
        else
        {
            Log.d(TAG, "learningmode");
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);
        }

        return config;
    }

    // set up the tangoUx thats gives user feedback in case of errors
    private TangoUx initTangoUx()
    {
        TangoUxLayout uxLayout = (TangoUxLayout) findViewById(R.id.tango_layout);
        TangoUx tangoUx = new TangoUx(this);
        tangoUx.setLayout(uxLayout);
        tangoUx.setUxExceptionEventListener(new UxExceptionEventListener() {
            @Override
            public void onUxExceptionEvent(UxExceptionEvent uxExceptionEvent) {
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_LYING_ON_SURFACE) {
                    Log.i(TAG, "Device lying on surface ");
                }
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_DEPTH_POINTS) {
                    Log.i(TAG, "Very few depth points in mPoint cloud ");
                }
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_FEATURES) {
                    Log.i(TAG, "Invalid poses in MotionTracking ");
                }
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_INCOMPATIBLE_VM) {
                    Log.i(TAG, "Device not running on ART");
                }
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOTION_TRACK_INVALID) {
                    Log.i(TAG, "Invalid poses in MotionTracking ");
                }
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOVING_TOO_FAST) {
                    Log.i(TAG, "Invalid poses in MotionTracking ");
                }
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FISHEYE_CAMERA_OVER_EXPOSED) {
                    Log.i(TAG, "Fisheye Camera Over Exposed");
                }
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FISHEYE_CAMERA_UNDER_EXPOSED) {
                    Log.i(TAG, "Fisheye Camera Under Exposed ");
                }
                if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_TANGO_SERVICE_NOT_RESPONDING) {
                    Log.i(TAG, "TangoService is not responding ");
                }
            }
        });
        return tangoUx;
    }


    // other tango assisting initialisation methods *******************************************************

    // get display rotation --> needed for rendering
    private void setDisplayRotation()
    {
        displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        surfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (isTangoConnected)
                    renderer.updateDisplayRotation(displayRotation);
            }
        });
    }

    // setup a listeners to receive notice when sreen orientation changes
    private void initDisplay()
    {
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }
    }

    // check whether pose is relocalised
    private synchronized void checkRelocalisation(TangoPoseData pose)
    {
        // check if there is a pose measuring from adf to start point of session
        if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE)
        {
            if (pose.statusCode == TangoPoseData.POSE_VALID)
            {
                // relocalissation was successful
                relocalised = true;
                toast("adf relocalised");
                relocalisedADFpose = pose;
                // set the content back to original position
                ObjectHolder.getObjectHolderInstance().setCubeToNewPosition(MathUtils.poseAsMatrix(pose));
            }
            else
            {
                relocalised = false;
            }
        }
    }

    // handles touch events
    private void setupTouchFunction()
    {
        // touchHandler identifies type of interaction
        TouchHandlerCallbackIF touchHandlerCallback = new TouchHandlerCallbackIF()
        {
            @Override
            public void touchHandlerCallback(TOUCH_MODE touchMode, float[] coords)
            {
                // check what kind of interaction has happened and activate functions regarding the
                // current app settings
                if (touchMode == TOUCH_MODE.LONG_TOUCH)
                {
                    if(scanBarcodeFunction)
                    {
                        // add the object for the barcode scan result
                        addRenderText(coords[0], coords[1], "barcode scanning..");
                        scanBarCode();
                    }
                    else if(scanImageForObjectFunction)
                    {
                        addRenderText(coords[0], coords[1], "object scanning..");
                        NetworkConnection.getNetworkConnection(MainActivity.this, new NetworkResponseCallbackIF()
                        {
                            @Override
                            public void networkResponseCallback(String response)
                            {
                                // add the object information result to the text object that shows the barcode result
                                ObjectHolder.getObjectHolderInstance().setText(response);
                            }

                            @Override
                            public void networkErrorCallback(String error)
                            {
                                ObjectHolder.getObjectHolderInstance().setText("fail to get information from server");
                            }
                        }).postImageRequest(currentImage);
                    }
                    else
                    {
                        if (objectFunction)
                        {
                            addRenderText(coords[0], coords[1], null);
                        }
                        if (measureFunction)
                        {
                            // add the point to the measurement line
                            addMeasurementLine(coords[0], coords[1]);
                        }
                        // placeholder interaction: position .obj
                        if(!objectFunction && ! measureFunction)
                            add3dObj(coords[0], coords[1]);
                    }
                }
                else if (touchMode==TOUCH_MODE.TAP)
                {
                    // calculate if object has been touched
                    calculateRayIntersection(coords[0], coords[1]);
                }
                else
                {
                    swipeCube(touchMode);
                }
            }
        };

        touchHandler = new TouchHandler(this, touchHandlerCallback);
        // sets a listener to the surfaceview layout element to get notice when user touched
        surfaceView.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                // in case of user interaction touch handler identifies interaction type
                touchHandler.onTouchEvent(event, v);
                return true;
            }
        });
    }



    // menu functions ********************************************************

    // handle open and close the menu when the user touched the button
    public void toggleMenu(View view)
    {
        // if menu is closed open it otherwise close
        if(popupWindow == null || (!popupWindow.isShowing()))
            openMenu();
        else
            popupWindow.dismiss();
    }

    // opens the menu by setting up the layout
    public void openMenu()
    {
        popupWindow.showAtLocation(surfaceView, Gravity.CENTER, 0, 0);
    }


    // set all elements in the menu with their values and interactions
    public void initialiseMenuLayout()
    {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        final View popupView = inflater.inflate(R.layout.options_window, null);
        popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        // close button
        ImageButton closeWindow = (ImageButton) popupView.findViewById(R.id.button_close_window);
        closeWindow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
            }
        });


        // activate or deactivate barcode scan mode
        final ToggleButton scanBarcodeButton = (ToggleButton) popupView.findViewById(R.id.barcodescanner_button);
        scanBarcodeButton.setChecked(scanBarcodeFunction);
        scanBarcodeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                scanBarcodeFunction = scanBarcodeButton.isChecked();
            }
        });

        // save and close app
        final ToggleButton imageForObjectRecognitionButton = (ToggleButton) popupView.findViewById(R.id.object_scan);
        imageForObjectRecognitionButton.setChecked(scanImageForObjectFunction);
        imageForObjectRecognitionButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                scanImageForObjectFunction = imageForObjectRecognitionButton.isChecked();
            }
        });

        // activate or deactivate to add text object
        final ToggleButton objectButton = (ToggleButton) popupView.findViewById(R.id.objects_button);
        objectButton.setChecked(objectFunction);
        objectButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                objectFunction = objectButton.isChecked();
                if(objectFunction)
                    renderer.removePlaceholderObject();
            }
        });

        // activate and deactivate measure line
        final ToggleButton measureLineButton = (ToggleButton) popupView.findViewById(R.id.measureline_button);
        measureLineButton.setChecked(measureFunction);
        measureLineButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                measureFunction = measureLineButton.isChecked();
                if(measureFunction)
                {
                    MeasureLine.getInstance().resetLine();
                }
                if(measureFunction)
                    renderer.removePlaceholderObject();
            }
        });
        // resets the measure line
        Button resetLineButton = (Button) popupView.findViewById(R.id.reset_line);
        resetLineButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                MeasureLine.getInstance().resetLine();
            }
        });

        // shows the current position and rotation of the device
        positionTextView = (TextView) popupView.findViewById(R.id.position_text);
        orientationTextView = (TextView) popupView.findViewById(R.id.position_text_orientation);
    }

    // functional methods **********************************************************

    public void scanBarCode()
    {
        // save adf for later synchronisation against it
        // can only save if it is not synchronising
        if(!syncADF)
            saveADF();
        syncADF = true;
        // set in the Object Holder the number of elements that need to be updated to new position after scan
        ObjectHolder.getObjectHolderInstance().setInterruptToUpdate();

        // start the barcode scanner activity
        Intent intent = new Intent(this, BarcodeScannerActivity.class);
        //startActivity(intent);
        startActivityForResult(intent, 1);
    }

    // method is called when application returns from barcode activity
    @Override
    protected void onActivityResult(int requestCode, int resultCode,  Intent intent)
    {
        String s = null;
        if(intent != null)
        {
            // get the detected barcode
            s = intent.getStringExtra("barcode");
        }
        // check if there is a valid barcode string
        if(s != null && s.length() > 2)
        {
            // send request to network to get object information
            NetworkConnection.getNetworkConnection(this, new NetworkResponseCallbackIF()
            {
                @Override
                public void networkResponseCallback(String response)
                {
                    // add teh object information result to the text object that shows the barcode result
                    ObjectHolder.getObjectHolderInstance().setText(response);
                }

                @Override
                public void networkErrorCallback(String error)
                {
                    toast("error in server connection");
                }
            }).postRequest(s);
        }
    }

    // add measurement line
    private void addMeasurementLine(float x, float y)
    {
        // calculate the corresponding point
        float[] point = getDepthPoint(x, y);
        if(point == null)
        {
            toast("error calculating point");
        }
        else
        {
            toast("point added");
            MeasureLine.getInstance().addPoint(point);
        }
    }

    // sets object to a new position : default
    private void add3dObj(float x, float y)
    {
        // find plane
        float[] planeFitTransform;
        synchronized (this) {
            planeFitTransform = calculatePlane(x, y, lastCameraPoseTimestamp);
        }
        if (planeFitTransform != null)
        {
            renderer.updateObjPos(planeFitTransform);
        }
        else
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "position invalid", Toast.LENGTH_SHORT).show();
                }
            });
            Log.d(TAG, "no plane transform");
        }
    }

    // object recognition -> add result to cube
    private void addRenderText(float x, float y, String text)
    {
        // calculate the plane to the point
        float[] planeFitTransform;
        synchronized (this) {
            planeFitTransform = calculatePlane(x, y, lastCameraPoseTimestamp);
        }

        if (planeFitTransform != null)
        {
            // add the object
            ObjectHolder.getObjectHolderInstance().setNewCube(planeFitTransform, text);
        }
        else
        {
            toast("invalid position");
            Log.d(TAG, "no plane transform");
        }
    }


    // method is called when swipe gesture
    private void swipeCube(TOUCH_MODE mode)
    {
        // calculate which cubes are touched by swipe gesture
        // take the middle of device
        boolean[] intersectedCubes = checkCubeIntersection(0.5f, 0.5f);
        renderer.swipe(mode, intersectedCubes);
    }


    // help methods for function related to user interaction **********************************************

    // checks which cubes are supposed to rotate
    // the method has to be synchronised against timestamp changes
    @Nullable
    private synchronized boolean[] checkCubeIntersection(float x, float y)
    {
        // get the open-gl 3d point to the coordinates
        float[] touchPoint = getDepthPoint(x, y);

        if(touchPoint == null)
            return null;

        // get the current position of the device
        TangoPoseData pose = TangoSupport.getPoseAtTime(
                lastCameraPoseTimestamp, TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE, TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                TangoSupport.ROTATION_IGNORED
        );
        float[] device = pose.getTranslationAsFloats();

        return Utils.checkObjectPointIntersection(touchPoint, device);
    }

    // finds the corresponding point x, y, z for the touched position
    @Nullable
    private float[] getDepthPoint(float x, float y)
    {
        // retrieve the last acquired point cloud
        TangoPointCloudData pointCloud = pointCloudManager.getLatestPointCloud();

        if (pointCloud == null)
            return null;

        // calculate the pose difference between the time the point cloud and the iamge are aquired
        TangoPoseData depthColorPose = TangoSupport.calculateRelativePose(
                lastCameraPoseTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH
        );

        float[] point;
        double[] ccTranslation = {0.0, 0.0, 0.0};
        double[] ccRotation = {0.0, 0.0, 0.0, 1.0};

        // calculates the actual point to the coordinates in the camera coordinate frame
        point = TangoSupport.getDepthAtPointNearestNeighbor(
                pointCloud, depthColorPose.translation, depthColorPose.rotation,
                x, y, displayRotation, ccTranslation, ccRotation
        );

        if (point == null)
            return null;

        // the point has to be transformed from camera into opengl coordinate frame
        TangoSupport.TangoMatrixTransformData cameraOpenGLTransform =
                TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.ROTATION_IGNORED);

        if (cameraOpenGLTransform.statusCode == TangoPoseData.POSE_VALID)
        {
            // to transform the point to the coordinate system it has to be multiplied by the matrix
            float[] colourPoint = new float[]{point[0], point[1], point[2], 1};
            float[] openGlPoint = new float[4];
            Matrix.multiplyMV(openGlPoint, 0, cameraOpenGLTransform.matrix, 0, colourPoint, 0);
            return openGlPoint;
        } else {
            Log.w(TAG, "Could not get depth camera transform at time " + pointCloud.timestamp);
        }
        return null;
    }

    // calculate the corresponding plane to a point
    @Nullable
    private synchronized float[] calculatePlane(float x, float y, double timestamp)
    {
        // acquire the last point cloud
        TangoPointCloudData pointCloud = pointCloudManager.getLatestPointCloud();

        if (pointCloud == null)
            return null;

        // calculate the difference of the pose between the time the dpeth and colour image was acquired
        TangoPoseData colorDepthPose = TangoSupport.calculateRelativePose(
                pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR
        );

        // zero translation and rotation
        double[] ppTranslation = {0.0, 0.0, 0.0};
        double[] ppRotation = {0.0, 0.0, 0.0, 1.0};

        TangoSupport.IntersectionPointPlaneModelPair intersectionPointPlaneModelPair = null;

        // in depth system
        try {
            // calculate the point and the plane
            intersectionPointPlaneModelPair =
                    TangoSupport.fitPlaneModelNearPoint(pointCloud,
                            ppTranslation, ppRotation, x, y, displayRotation,
                            colorDepthPose.translation, colorDepthPose.rotation);
        }
        catch(Exception e)
        {
            Log.d(TAG, "error fitting plane");
        }
        if(intersectionPointPlaneModelPair == null)
            return null;

        // as the point is in the depth coordinate frame it has to be transformed to the opengl frame
        TangoSupport.TangoMatrixTransformData depthOpenGLTransform =
                TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.ROTATION_IGNORED);

        if (depthOpenGLTransform.statusCode == TangoPoseData.POSE_VALID)
        {
            double[] point =  intersectionPointPlaneModelPair.intersectionPoint;
            float[] depthPoint = new float[]{(float)point[0], (float)point[1], (float)point[2], 1};
            float[] openGlPoint = new float[4];
            Matrix.multiplyMV(openGlPoint, 0, depthOpenGLTransform.matrix, 0, depthPoint, 0);
           // renderer.addPoint(openGlPoint);

            // calculated a matrix from the point and plane
            float[] openGlTPlane = MathUtils.calculatePlaneTransform(
                    intersectionPointPlaneModelPair.intersectionPoint,
                    intersectionPointPlaneModelPair.planeModel, depthOpenGLTransform.matrix);

            return openGlTPlane;
        } else {
            Log.w(TAG, "Can't get depth camera transform at time " + pointCloud.timestamp);
            return null;
        }
    }

    // calculate the ray of a touched point
    // and possible intersections with virtual objects
    private void calculateRayIntersection(float x, float y)
    {
        float xc = (x - 0.5f)*2;
        // coord sys y top
        float yc = -2*(y - 0.5f);

        // calculate the angles of the field of view of the camera
        TangoCameraIntrinsics intrinsics = TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(TangoCameraIntrinsics.TANGO_CAMERA_COLOR, TangoSupport.ROTATION_0);
        double fovs[] = Utils.calculateFov(intrinsics);
        double xFov = 0.5*fovs[0];
        double yFov = 0.5*fovs[1];

        // get the angles for the chosen point
        double angleX = xFov*xc;
        double angleY = yFov*yc;


        // middle vector without any rotation
        Vector3 v = new Vector3(0, 0, -1);

        Vector3 xAxis = new Vector3(1, 0, 0);
        Vector3 yAxis = new Vector3(0, 1, 0);

        // rotate the middle vector the specified angles corresponding to the point
        Quaternion rotation = new Quaternion(xAxis, angleY);
        rotation.multiply(new Quaternion(yAxis, -angleX));

        TangoPoseData devicePosition = TangoSupport.getPoseAtTime(
                lastCameraPoseTimestamp,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE, TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                TangoSupport.ROTATION_IGNORED
        );

        float[] deviceRotation = devicePosition.getRotationAsFloats();
        float[] deviceTranslation = devicePosition.getTranslationAsFloats();

        // also consider the rotation of the device
        Quaternion qat = new Quaternion(deviceRotation[3], deviceRotation[0], deviceRotation[1], deviceRotation[2]);
        rotation.multiply(qat);

        // set the rotation of the vector pointing to the corresponding points
        v.rotateBy(rotation);

        // normalize the vector
        float[] rotVector = MathUtils.normalizeVector(new float[]{(float)v.x, (float)v.y, (float)v.z});

        // check the intersection with virtual content
        Utils.checkObjectIntersection(deviceTranslation, rotVector);
    }

    // area description
    private void saveADF()
    {
        // adf can only be saved when it is not synchronising against another
        if(syncADF)
        {
            toast("cannot save adf -> synch mode");
            return;
        }

        String adfname = "ADF";
        // the saving procedure is happening in the SaveAdf class
        final SaveADF save = new SaveADF(new SaveADF.SaveADFcallback()
        {
            @Override
            public void onSaveAdfFailed(String adfName)
            {
                toast("error saving adf");
            }

            // is returned when the adf was saved successfully
            @Override
            public void onSaveAdfSuccess(String adfName, String adfUuid)
            {
                toast("adf saved");
            }
        }, tango, adfname );
        save.saveADF();
    }


    // shows the user an information message
    private void toast(final String message)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run()
            {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
