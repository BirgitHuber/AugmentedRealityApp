package com.birgit.projectba.graphicHolderClasses;

import android.util.Log;

import com.birgit.projectba.ARrenderer;
import com.birgit.projectba.utils.MathUtils;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

import java.util.Stack;


// static class to hold the opengl objects
// can be accessed by all other classes

public class ObjectHolder
{
    private static ObjectHolder objectHolder = new ObjectHolder();
    private static String TAG = ObjectHolder.class.getSimpleName();

    // maximum number of cubes in a scene
    public final static int MAX_CUBES = 25;
    // current number of cubes created in session
    private int totalNumCubes = 0;
    // default size for a cube
    private float cubeSize = 0.2f;
    private ARTextField[] textFields = new ARTextField[MAX_CUBES];
    // int-codes represent if one cube has updated (with index of it), nothing happened -1, or all updated -2

    // number of cubes at one position -> needed to be able to place another one on top
    private int indexCollision[] = new int[MAX_CUBES];

    // stack of text field that are waiting to receive their text from server response
    private Stack<Integer> processingTextFields = new Stack<>();
    // number of cubes that needs to get translation after tango-service start again
    private int interruptCode = -1;

    // default text
    private static String defaultText = "Tango. " +
            "Tango is a platform that uses computer vision to give devices the ability to understand their position relative to the world around them. A Tango enabled device is an Android device with a wide-angle camera, a depth sensing camera, accurate sensor timestamping, and a software stack that enables application developers to use motion tracking, area learning and depth sensing. Thousands of developers have purchased these devices to create experiences to explore physical space around the user, including precise navigation without GPS, windows into virtual 3D worlds, measurement and scanning of spaces, and games that know where they are in the room and whats around them." +
            "You will need a Tango enabled device, like the Lenovo Phab 2 Pro, in order to run and test any apps you develop.";


    private ObjectHolder()
    {
        // Singleton constructor
    }


    public static ObjectHolder getObjectHolderInstance()
    {
        if(objectHolder == null)
            objectHolder = new ObjectHolder();
        return objectHolder;
    }


    // from MainActivity **************************************************************

    // returns the positions of the cubes to the main activity,
    // e.g. for calculating intersection with user action
    public float[][] getCubePositions()
    {
        float[][] cubePositions = new float[MAX_CUBES][3];

        // return just as many cubes are there
        for(int i=0; i<MAX_CUBES && i<totalNumCubes; i++)
        {
            Vector3 vPosition = textFields[i].getPosition();
            cubePositions[i] = new float[]{(float)vPosition.x, (float)vPosition.y, (float)vPosition.z};
        }
        if(totalNumCubes >= MAX_CUBES)
            return cubePositions;

        float[][] res = new float[totalNumCubes][3];
        for(int i=0; i<totalNumCubes; i++)
            res[i] = cubePositions[i];
        return res;
    }

    // adds a new cube from main activity
    public synchronized void setNewCube(float[] matrix, String text)
    {
        // set the position and orientation of the cube
        Matrix4 cubeMat = new Matrix4(matrix);
        Vector3 position = cubeMat.getTranslation();
        Quaternion rotation = new Quaternion().fromMatrix(cubeMat);

        // check if cube collides with another one
        position = calculatePositionWithCollisions(position);

        // replacement necessary if more than max number
        int index = totalNumCubes % MAX_CUBES;

        // create the ar-text field
        // set a default text if there is no text
        if(text == null || text.length() < 1)
        {
            text = "object " + index;
            Log.d(TAG, "text "+text);
        }
        else if(text.equals("barcode scanning..") || text.equals("object scanning.."))
        {
            // save the textfield that are waiting for a result to display
            processingTextFields.add(index);
        }
        textFields[index] = new ARTextField(position, cubeSize, rotation, text);

        totalNumCubes++;
        // add the cube to the renderer
        ARrenderer.getRendererInstance().addNewCube(index, textFields[index]);
    }

    // sets the cube to a new position on top of another one in case of collotion with other cube
    private Vector3 calculatePositionWithCollisions(Vector3 position)
    {
        // check
        int collidedCubeIndex = checkCubesForCollision(position);
        // calculate the new position of the cube on top of the stack
        if(collidedCubeIndex >= 0)
        {
            Log.d(TAG, "set new position");
            // if there is a colliding cube set it on top
            Vector3 newposition = textFields[collidedCubeIndex].getPosition();

            // check if there are more on the stack
            int offset = indexCollision[collidedCubeIndex];
            indexCollision[collidedCubeIndex]++;
            // set the new one on top of the stack
            position = new Vector3(newposition.x, newposition.y , newposition.z);
            position.y += ((cubeSize/2)+(cubeSize*offset));
        }
        return position;
    }

    // checks if a previously added cube collides with another one
    private int checkCubesForCollision(Vector3 position)
    {
        // check all cubes in scene
        for(int i=0; i<MAX_CUBES && i < totalNumCubes; i++)
        {
            // get the distnace between the two position
            double distance = MathUtils.distanceBetween2Points(position, textFields[i].getPosition());
            Log.d(TAG, "distance "+distance);
            if(distance <= cubeSize)
            {
                Log.d(TAG, "colloision");
                // if the distance is smaller than the cube size collision has occurred
                // return the index of the interfering cube
                return i;
            }
        }
        return -1;
    }

    // *********************************************************************************

    // removes all cubes from rendering --> e.g. delete scene
    public void removeAllCubesFromRendering()
    {
        for(int i=0; i<totalNumCubes && i<MAX_CUBES; i++)
        {
            textFields[i] = null;
        }
        textFields = new ARTextField[MAX_CUBES];
        totalNumCubes = 0;
    }


    // getter and setter
    public synchronized int getNumberOfCubes()
    {
        return totalNumCubes;
    }

    // changes the text of a cube **********************************************++
    public void setText(String text)
    {
        // get the last element waiting longest for a text and set it
        int index = processingTextFields.remove(0);
        changeText(index, text);
    }

    // changes the text for a specific element
    public void changeText(int index, String text)
    {
        if(text == null)
            text = defaultText;
        textFields[index].changeRenderText(text);
    }

    // if tango service is interrupted and objects should later be translated to new position
    public void setInterruptToUpdate()
    {
        interruptCode = totalNumCubes;
    }

    // sets all objects that are created before interruption to the new position
    public void setCubeToNewPosition(float[] transformationMatrix)
    {
        Log.d(TAG, "set cube t offset");
        // no element to change position
        if(interruptCode < 0)
            return;

        for(int i=0; i<interruptCode; i++)
        {
            changeCubePosition(i, transformationMatrix);
        }
        interruptCode = -1;
    }

    // update the cube position
    public void changeCubePosition(int index, float[] matrix)
    {
        Log.d(TAG, "change cube pos: #"+index);
        textFields[index].translate(matrix);
        //updatedCube = index;
        ARrenderer.getRendererInstance().updateCubePosition(index, textFields[index]);
    }


}
