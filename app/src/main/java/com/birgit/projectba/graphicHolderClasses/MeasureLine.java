package com.birgit.projectba.graphicHolderClasses;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.birgit.projectba.ARrenderer;
import com.birgit.projectba.utils.MathUtils;

import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.Plane;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// class holds the measure line elements
public class MeasureLine
{
    private static MeasureLine measureLine = null;

    // line
    private Line3D line;
    private Stack<Vector3> points = new Stack<>();

    // plane
    private List<Plane> measureTexts = new ArrayList<>();
    private Material planeMaterial;
    private Material lineMaterial;
    private List<Bitmap> bitmaps = new ArrayList<>();

    private float totalDistance = 0;

    // plane size
    private float height = 0.05f;
    private float width = 0.3f;


    private MeasureLine()
    {
        // private constructor -> only one class instance should be possible
        lineMaterial = new Material();
        lineMaterial.setColor(Color.RED);

        planeMaterial = new Material();
        planeMaterial.setColor(Color.GRAY);
    }

    // if another class needs the MeasureLine; the instance is returned to that
    public static MeasureLine getInstance()
    {
        if(measureLine == null)
        {
            // if there is no instance; create a new one
            measureLine = new MeasureLine();
        }
        // if there is already an instance return that
        return measureLine;
    }


    // method called by MainActivity --> adds a new point
    public synchronized void addPoint(float[] point)
    {
        // the points are needed as Stack<Vector3>; so transform
        Vector3 newPoint = new Vector3(point[0], point[1], point[2]);
        if(points == null)
            points = new Stack<>();
        if(!points.isEmpty())
        {
            // if there is more than one point; calculate distance between them
            Vector3 lastPoint = points.peek();
            float distance = MathUtils.distanceBetween2Points(lastPoint, newPoint);
            totalDistance += distance;

            // add the distance as plane displayed between the two points
            Vector3 position = MathUtils.middleOf2Points(lastPoint, newPoint);
            setNewPlane(position);
        }
        points.add(new Vector3(point[0], point[1], point[2]));

        // if there is a line add it to render
        if(points.size() > 1)
            ARrenderer.getRendererInstance().updateMeasureLine(getMeasureTextToRender(), getLineToRender());
    }

    // calculates the new plane
    private void setNewPlane(Vector3 position)
    {
        // create a plane with one texture tile
        Plane textPlane = new Plane(width, height, 1, 1);

        // set the text for the plane
        NumberFormat format = new DecimalFormat("0.00");
        format.setRoundingMode(RoundingMode.HALF_UP);
        String renderText = format.format(totalDistance);

        Material planeMaterial = new Material();
        planeMaterial.setColor(Color.GRAY);
        // get the distance as bitmap used for the rendering texture
        Bitmap text = TextureManager.combineBitmap(renderText, renderText.length(), 1);
        bitmaps.add(text);
        Texture texture = new Texture("rendertext", text);
        try {
            planeMaterial.addTexture(texture);
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        // set attributes to the plane
        textPlane.setPosition(position);
        textPlane.setMaterial(planeMaterial);
        textPlane.setDoubleSided(true);
        measureTexts.add(textPlane);
    }

    // returns the line to render
    private Line3D getLineToRender()
    {
        line = new Line3D(points, 50);
        line.setMaterial(lineMaterial);

        return line;
    }

    // returns the plane to render
    private Plane getMeasureTextToRender()
    {
        //Plane[] measures = measureText.toArray(new Plane[measureText.size()]);
        Plane plane = measureTexts.get(measureTexts.size() -1);
        return plane;
    }

    public Plane[] getAllMeasureText()
    {
        Plane[] texts = measureTexts.toArray(new Plane[measureTexts.size()-1]);
        return texts;
    }

    // deletes the measure line from rendering
    public void resetLine()
    {
        // delete all elements
        points.clear();

        if(!measureTexts.isEmpty())
        {
            int index = measureTexts.size();

            for (int i = 0; i < index; i++)
            {
                // delete the bitmap for the text display as it needs lots of memory
                measureTexts.get(0).getMaterial().unbindTextures();
                measureTexts.remove(0);
                bitmaps.get(0).recycle();
            }
            measureTexts = new ArrayList<>();
        }
        totalDistance = 0;
        // reset line from renderer
        ARrenderer.getRendererInstance().resetLine();
    }

}
