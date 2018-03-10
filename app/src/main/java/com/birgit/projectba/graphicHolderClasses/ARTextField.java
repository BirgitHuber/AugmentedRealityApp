package com.birgit.projectba.graphicHolderClasses;


import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.birgit.projectba.appFunctions.TouchHandler.TOUCH_MODE;
import com.birgit.projectba.utils.MathUtils;

import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Plane;


public class ARTextField
{
    private String TAG = ARTextField.class.getSimpleName();

    private Vector3 position;
    private float size;
    private Quaternion rotation;

    private Plane[] cubeAsPlane = new Plane[6];
    private final Vector3 axisVector[] = new Vector3[3];
    private Vector3[] planePositions = new Vector3[6];
    private Quaternion[] quaternions = new Quaternion[6];
    // axis like functions
    private Vector3[] planes = new Vector3[6];
    private Vector3.Axis[] planeAxis = new Vector3.Axis[6];

    // connects which side has which material
    private int[] currentRotation = new int[6];
    private Material[] materials = new Material[6];
    private Texture[] textures = new Texture[4];
    private int[] colors = new int[6];

    private String[] text = new String[4];
    private Bitmap[] textbitmap = new Bitmap[4];

    private String renderText = "";
    private String[] sidetexts;


    public ARTextField(Vector3 position, float size, Quaternion rotation, String text)
    {
        this.position = new Vector3(position.x, position.y+(size/2), position.z);
        this.size = size;
        this.rotation = rotation;

        if(text != null)
            renderText = text;

        setupCube();
    }


    // initialisation ***************************************************************

    // sets up the cube
    private void setupCube()
    {
        // initialise all the values
        initValues();
        setPosition();
        processString();
        setupMaterials();

        // apply to the cube sides
        for(int i=0; i<6; i++)
        {
            cubeAsPlane[i] = new Plane(size, size, 1, 1, planeAxis[i]);

            cubeAsPlane[i].setDoubleSided(true);
            cubeAsPlane[i].setPosition(planePositions[i]);
            cubeAsPlane[i].setMaterial(materials[i]);
            currentRotation[i] = i;
        }
        setDefaultSideRotation();
    }

    // initialise the basic values for the planes
    private void initValues()
    {
        planeAxis[0] = Vector3.Axis.Z;
        planeAxis[1] = Vector3.Axis.X;
        planeAxis[2] = Vector3.Axis.Z;
        planeAxis[3] = Vector3.Axis.X;
        planeAxis[4] = Vector3.Axis.Y;
        planeAxis[5] = Vector3.Axis.Y;

        colors[0] = Color.BLUE;
        colors[1] = Color.GREEN;
        colors[2] = Color.CYAN;
        colors[3] = Color.MAGENTA;
        colors[4] = Color.RED;
        colors[5] = Color.YELLOW;

        axisVector[0] = new Vector3(1, 0, 0);
        axisVector[1] = new Vector3(0, 1, 0);
        axisVector[2] = new Vector3(0, 0, 1);

        planes[0] = new Vector3(0, 0, 1);   // z
        planes[1] = new Vector3(1, 0, 0);   // x
        planes[2] = new Vector3(0, 0, -1);
        planes[3] = new Vector3(-1, 0, 0);
        planes[4] = new Vector3(0, 1, 0);       // y
        planes[5] = new Vector3(0, -1, 0);
    }

    // set the rotation that planes are in upright position
    private void setDefaultSideRotation()
    {
        Vector3 x = planes[1];
        Vector3 x1 = Vector3.getAxisVector(Vector3.Axis.X);
        Vector3 y = planes[4];
        Vector3 y1 = Vector3.getAxisVector(Vector3.Axis.Y);
        Vector3 z = Vector3.getAxisVector(Vector3.Axis.Z);

        // calculate the rotation of the sides
        quaternions[1] = new Quaternion(x1,  90);
        quaternions[2] = new Quaternion(y1,  180);
        quaternions[3] = new Quaternion(y1,  180);
        quaternions[3].multiply( new Quaternion(x1, -90));

        // apply the rotations
        cubeAsPlane[1].rotate(quaternions[1]);
        cubeAsPlane[2].rotate(quaternions[2]);
        cubeAsPlane[3].rotate(quaternions[3]);
    }

    // sets the position for each plane
    private void setPosition()
    {
        for(int i=0; i<planes.length; i++)
        {
            planes[i] = MathUtils.normalizeVector3(planes[i]).multiply(size/2);

            planePositions[i] = new Vector3(planes[i].x, planes[i].y, planes[i].z);
            planePositions[i].add(position);
        }
    }

    // material methods **************************************************************

    // set the text to the four sides
    private void processString()
    {
        // max number of signs per side
        int maxLetters = 12*12;
        // four side texts
        sidetexts = new String[4];

        // cut the rest if text is to long
        if(renderText.length() >= maxLetters*4)
        {
            renderText = renderText.substring(0, (4*maxLetters -1));
        }

        // divide the text to four sides
        for(int i=0; i<4; i++)
        {
            // begin substring
            int bind = i*maxLetters;
            // end substring
            int eind = (i+1)*maxLetters-1;

            // set all symbols to this side
            if(renderText.length() >= 12*12*(i+1))
            {
                sidetexts[i] = renderText.substring(bind, eind);
            }
            else
            {
                // set rest string to side
                sidetexts[i] = renderText.substring(bind, (renderText.length() ));
                break;
            }
        }
    }

    // sets the materials for each side;
    // colour materials and text; gets the text from the textureManager as bitmap
    private void setupMaterials()
    {
        // sets the colour for each side
        for(int i=0; i<6; i++)
        {
            materials[i] = new Material();
            materials[i].setColor(colors[i]);
        }
        // sets the texts to the side materials
        for(int i=0; i<text.length; i++)
        {
            // if there exists a text for that side add it to material
            if(sidetexts[i] != null)
            {
                // get the text as bitmap
                textbitmap[i] = TextureManager.combineBitmap(sidetexts[i]);
                textures[i] = new Texture("rendertext", textbitmap[i]);

                try
                {
                    materials[i].addTexture(textures[i]);
                }
                catch (ATexture.TextureException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }


    // getter methods ****************************************************************

    public Plane[] getPlanes()
    {
        return cubeAsPlane;
    }

    public Vector3 getPosition()
    {
        return position;
    }


    // deletes the bitmaps of the text
    public void delete()
    {
        for(int i=0; i<4; i++)
        {
            if(textbitmap[i] != null)
            {
                textbitmap[i].recycle();
                materials[i].removeTexture(textures[i]);
            }
            materials[i].unbindTextures();
        }
    }


    // change position ***********************************************************

    public void translate(float[] matrix)
    {
        Matrix4 matrix4 = new Matrix4(matrix);
        Vector3 translation = matrix4.getTranslation();
        changePosition(translation);
    }

    private void changePosition(Vector3 translation)
    {
        // apply the new position to all plane elements
        this.position = translation;
        setPosition();
        for(int i=0; i<6; i++)
            cubeAsPlane[i].setPosition(planePositions[i]);
    }

    // set a new rendertext
    public void changeRenderText(String text)
    {
        // delete the plane materials
        delete();
        renderText = text;
        // process the string to divide to 4 sides
        processString();
        // set the string to the materials
        setupMaterials();

        // apply the new materials to tha planes
        for(int i=0; i<cubeAsPlane.length; i++)
        {
            cubeAsPlane[i].setMaterial(materials[i]);
        }
    }

    // user interaction **********************************************************

    // swipe left or right  --> change the materials
    public void rotateCube(TOUCH_MODE mode)
    {
        int rotationMirror[] = new int[6];

        switch(mode)
        {
            case SWIPE_RIGHT:
                for(int i=0; i<4; i++)
                {
                    // change the material with the neighbour plane
                    int newMaterial;
                    if(i==0)
                        newMaterial = currentRotation[3];
                    else
                        newMaterial = currentRotation[i-1];
                    cubeAsPlane[i].setMaterial(materials[newMaterial]);
                    rotationMirror[i] = newMaterial;
                }
                break;
            case SWIPE_LEFT:
                // all four sides (1-4) change materials
                for(int i=0; i<4; i++)
                {
                    // change material with neighbour plane
                    int newMaterial = (i+1) % 4;
                    cubeAsPlane[i].setMaterial(materials[currentRotation[newMaterial]]);
                    rotationMirror[i] = currentRotation[newMaterial];
                }
                break;
        }
        for(int i=0; i<4; i++)
        {
            // remember to which plane a certain material is set
            currentRotation[i] = rotationMirror[i];
        }
    }
}
