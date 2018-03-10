package com.birgit.projectba;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;

import com.birgit.projectba.appFunctions.TouchHandler.TOUCH_MODE;
import com.birgit.projectba.graphicHolderClasses.ARTextField;
import com.birgit.projectba.graphicHolderClasses.ObjectHolder;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.loader.ParsingException;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.Renderer;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import static com.birgit.projectba.graphicHolderClasses.ObjectHolder.MAX_CUBES;


public class ARrenderer extends Renderer
{
    public static ARrenderer rendererInstance = null;

    private static final String TAG = ARrenderer.class.getSimpleName();

    // background
    private ScreenQuad background;
    private ATexture cameraTexture;
    private float[] textureCoords0 = new float[]{0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};

    // objects to rendering line
    private Line3D line;
    private List<Plane> measureText;

    // objects to rendering text cube
    private ARTextField[] textFields;

    // imported object to render as placeholder
    private Object3D object3D;

    // general scene configurations
    private boolean projectionMatrixSet;

    // materials used for colouring the objects
    private int colour;
    private Material colourMaterial;
    private Material redColourMaterial;

    // privae constructor: only one instance of renderer class should exist
    private ARrenderer(Context context)
    {
        super(context);
        textFields = new ARTextField[MAX_CUBES];
        measureText = new ArrayList<>();

        colour = ContextCompat.getColor(context, R.color.color_primary);
    }

    // access from Main class: needs context
    public static ARrenderer initRenderer(Context context)
    {
        if(rendererInstance == null)
            rendererInstance = new ARrenderer(context);
        return rendererInstance;
    }

    // access from other classes without context
    public static ARrenderer getRendererInstance()
    {
        return rendererInstance;
    }


    // this method is called once when creating the instance
    @Override
    protected void initScene()
    {
        // Add a light to the scene
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        // set the background
        if (background == null)
        {
            background = new ScreenQuad();
            background.getGeometry().setTextureCoords(textureCoords0);
        }
        // connect the background texture with the tango camera
        Material videoBackgroundMaterial = new Material();
        videoBackgroundMaterial.setColorInfluence(0);
        cameraTexture = new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            videoBackgroundMaterial.addTexture(cameraTexture);
            background.setMaterial(videoBackgroundMaterial);
        }
        catch (ATexture.TextureException e) {
            Log.e(TAG, "fail setting camera texture", e);
        }
        // add the background to the scene
        getCurrentScene().addChildAt(background, 0);

        // initialises the material
        colourMaterial = new Material();
        colourMaterial.setColor(Color.GREEN);
        redColourMaterial = new Material();
        redColourMaterial.setColor(colour);
    }


    @Override
    protected void onRender(long elapsedTime, double deltaTime)
    {
         super.onRender(elapsedTime, deltaTime);
    }

    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height)
    {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        projectionMatrixSet = false;
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {}

    @Override
    public void onTouchEvent(MotionEvent event) { }


    // set in activity class *********************************************************

    // sets the projection matrix to the scene
    public void setProjectionMatrix(float[] matrixFloats)
    {
        getCurrentCamera().setProjectionMatrix(new Matrix4(matrixFloats));
        projectionMatrixSet = true;
    }

    // check from main activity is projection matrix is set
    public boolean isProjectionMatrixConfigured()
    {
        return projectionMatrixSet;
    }

    // sets the view matrix: that is calculated by the current pose of the device in the camera object
    public void updateViewMatrix(TangoPoseData cameraPose)
    {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);

        // set the device pose to the scene camera
        getCurrentCamera().setRotation(quaternion.conjugate());
        getCurrentCamera().setPosition(translation[0], translation[1], translation[2]);

        // the measure text should have same rotation as device
        for(int i=0; i<measureText.size(); i++)
        {
            // reset the rotation for each plane
            measureText.get(i).setRotation(quaternion.conjugate().inverse());
        }
    }

    // update the coordinates of the background when display rotation has changed
    public void updateDisplayRotation(int rotation)
    {
        if (background == null)
            background = new ScreenQuad();

        // tango can now automatically update the coordinates for the background based on the display rotation
        float[] textureCoords = TangoSupport.getVideoOverlayUVBasedOnDisplayRotation(textureCoords0, rotation);
        background.getGeometry().setTextureCoords(textureCoords, true);
        background.getGeometry().reload();
    }


    // returns the id of the camera texture to the main activity that tango can connect to it
    public int getTextureId()
    {
        if (cameraTexture == null)
            return -1;
        return cameraTexture.getTextureId();
    }

    // user input // update the rotation of the textfields
    public void swipe(TOUCH_MODE mode, boolean[] intersected)
    {
        if(intersected == null)
            return;

        int maxCubes = MAX_CUBES;

        if(mode == TOUCH_MODE.SWIPE_LEFT || mode == TOUCH_MODE.SWIPE_RIGHT)
        {
            for(int i=0; i<maxCubes; i++)
            {
                if(textFields[i] == null)
                    break;
                if(intersected[i])
                {
                    textFields[i].rotateCube(mode);
                }
            }
        }
    }


    // MeasureLine ********************************************************
    // deletes the measure line from the rendering
    public synchronized void resetLine()
    {
        if(line != null)
        {
            getCurrentScene().removeChild(line);
            line = null;
        }

        if(!measureText.isEmpty())
        {
            int index = measureText.size();
            for (int i = 0; i < index; i++)
            {
                // delete the texture of the plane
                getCurrentScene().removeChild(measureText.get(0));
                measureText.get(0).getMaterial().unbindTextures();
                measureText.remove(0);
            }
        }
    }

    // called from the MeasureLineClass when a new point was added
    public synchronized void updateMeasureLine(Plane plane, Line3D renderline)
    {
        // the line has to be removed to add a new point to it
        if(line != null)
            getCurrentScene().removeChild(line);

        // set the line
        line = renderline;
        getCurrentScene().addChild(line);

        // add the new measure text plane
        measureText.add(plane);
        getCurrentScene().addChild(measureText.get(measureText.size()-1));
    }


    // TextField ************************************************************

    // add the elements of the cube text object to the scene
    private void addCubeToRender(ARTextField cubeToRender)
    {
        Plane[] toDraw = cubeToRender.getPlanes();
        for(int i=0; i<toDraw.length; i++)
        {
            getCurrentScene().addChild(toDraw[i]);
        }
    }

    // remove all elements of the cube from the scene
    private void removeCubeFromRendering(ARTextField cube)
    {
        Plane[] toDraw = cube.getPlanes();
        for(int i=0; i<toDraw.length; i++)
        {
            getCurrentScene().removeChild(toDraw[i]);
        }
    }

    // destroy the cube
    private void deleteCubeMaterials(ARTextField cube)
    {
        Plane[] toDraw = cube.getPlanes();
        for(int i=0; i<toDraw.length; i++)
        {
            toDraw[i].getMaterial().unbindTextures();
            getCurrentScene().removeChild(toDraw[i]);
            toDraw[i].destroy();
        }
        // delete the cube for deallocation of its storage ressources
        cube.delete();
    }

    // if new cube has been created; add it to render
    public void addNewCube(int index, ARTextField textField)
    {
        // check if cube replaces another
        if (textFields[index] != null)
        {
            deleteCubeMaterials(textFields[index]);
            textFields[index].delete();
        }
        // set the new cube and add it to render
        textFields[index] = textField;
        addCubeToRender(textFields[index]);
    }

    // changes the position of a cube
    public void updateCubePosition(int index, ARTextField textField)
    {
        // remove from position -> needed to add elements from scene
        if (textFields[index] != null)
        {
            removeCubeFromRendering(textFields[index]);
        }
        // add the new cube to render
        textFields[index] = textField;
        addCubeToRender(textFields[index]);
    }


    // loads a predefined .obj model from the resources *************************
    private void addObjectFromRes()
    {
        // load the object from the resource folder and compile it
        LoaderOBJ objParser = new LoaderOBJ(this, R.raw.object);
        try
        {
            objParser.parse();
        }
        catch (ParsingException e)
        {
            e.printStackTrace();
        }

        // set the material for the object
        colourMaterial.setColorInfluence(0);
        colourMaterial.enableLighting(true);
        colourMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());

        object3D = objParser.getParsedObject();
        object3D.setMaterial(colourMaterial);

        // set the object to the position
        object3D.rotate(Vector3.Axis.X, 90);
        object3D.setScale(0.001);
        getCurrentScene().addChild(object3D);
    }

    // update the position of the 3d.obj
    public void updateObjPos(float[] transform)
    {
        // apply the transformation matrix to the object
        Matrix4 transformationMat = new Matrix4(transform);
        Vector3 newPosition = transformationMat.getTranslation();
        Quaternion rotation = new Quaternion().fromMatrix(transformationMat);

        // add object if it is not existing
        if(object3D == null)
            addObjectFromRes();

        // set it to the position and rotate it according the matrix
        object3D.setRotation(rotation);
        object3D.setPosition(newPosition);
    }

    // remove the .obj 3d-object if it is not needed
    public void removePlaceholderObject()
    {
        getCurrentScene().removeChild(object3D);
        object3D = null;
    }

}
