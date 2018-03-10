package com.birgit.projectba.utils;

import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;

import com.birgit.projectba.graphicHolderClasses.ObjectHolder;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import static com.birgit.projectba.utils.MathUtils.crossProduct;
import static com.birgit.projectba.utils.MathUtils.getVectorLength;
import static com.birgit.projectba.utils.MathUtils.substractVector;


public class Utils
{
    public static void saveImage(byte[] imageData, double timestamp)
    {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        if(!path.exists())
        {
            path.mkdirs();
        }
        int time = (int) timestamp;
        String filename = "image"+time+".jpg";
        File file2write = new File(path, filename);

        try
        {
            FileOutputStream fout = new FileOutputStream(file2write);
            fout.write(imageData);
            fout.flush();
            fout.close();

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    public static byte[] getCurrentImageJPEG(TangoImageBuffer currentImage)
    {
        // change picture to jpeg
        int width = currentImage.width;
        int height = currentImage.height;
        byte[] imageData = currentImage.data.array();
        // picture format is NV21 -> number 17
        int format = currentImage.format;
        double timestamp = currentImage.timestamp;

        // converts the NV21 format to jpeg
        YuvImage yuvImage = new YuvImage(imageData, format, width, height, null);

        // rect should cover whole image
        Rect rect = new Rect(0, 0, width, height);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        yuvImage.compressToJpeg(rect, 100, baos);

        byte[] jpegImage = baos.toByteArray();

        return jpegImage;
    }


    public static double[] calculateFov(TangoCameraIntrinsics intrinsics)
    {
        // alpha = 2*arctan(d/2f)
        // https://en.wikipedia.org/wiki/Angle_of_viewhttps://en.wikipedia.org/wiki/Angle_of_view
        double width = intrinsics.width;
        double height = intrinsics.height;

        double fx = intrinsics.fx;
        double fy = intrinsics.fy;

        double fxm = (0.0254/300)*fx;
        double fym = (0.0254/300)*fy;

        double mwidth = (0.0254/300)*intrinsics.width;
        double mheight = (0.0254/300)*intrinsics.height;

        double rx = 2*Math.atan(width/(2*fx));
        double ry = 2*Math.atan(height/(2*fy));

        double rxm = 2*Math.atan(mwidth/(2*fxm));
        double rym = 2*Math.atan(mheight/(2*fym));

        double rxD = Math.toDegrees(rx);
        double ryD = Math.toDegrees(ry);
        double rxDm = Math.toDegrees(rxm);
        double ryDm = Math.toDegrees(rym);

        Log.d("degree: fov ", "x: "+rxD+"  ->"+rxDm);
        Log.d("degree: fov ", "y: "+ryD+"  ->"+ryDm);

        return new double[]{rxD, ryD};
    }

    public static String[] poseString(TangoPoseData pose)
    {
        String[] s = new String[2];

        float translation[] = pose.getTranslationAsFloats();
        float rotation[] = MathUtils.getRotationInDegrees(pose.getRotationAsFloats());

        NumberFormat format = new DecimalFormat("0.00");
        format.setRoundingMode(RoundingMode.HALF_UP);

        String tx = format.format(translation[0]);
        String ty = format.format(translation[1]);
        String tz = format.format(translation[2]);

        s[0] = "X: "+tx+"\n Y: "+ty+"\n Z:" +tz;
        s[1] = "X: "+(int) rotation[0]+"\n Y: "+(int) rotation[1]+"\n Z:" +(int) rotation[2];

        return s;
    }


    // calculate intersection of a line between 2 points and a virtual content
    public static void checkObjectIntersection(float[] start, float[] vector)
    {
        float[][] cubePositions = ObjectHolder.getObjectHolderInstance().getCubePositions();
        boolean touched[] = new boolean[cubePositions.length];

        // calculates a line from the start point in the direction of the vector
        // and the distance from each object to the line
        for(int i=0; i<cubePositions.length; i++)
        {
            float vd[] = MathUtils.substractVector(cubePositions[i], start);

            float cp[] = MathUtils.crossProduct(vector, vd);
            float distance = MathUtils.getVectorLength(cp);

            // calculate distance between device and object
            float[] vtemp = substractVector(cubePositions[i], start);
            float distanceOffset = MathUtils.getVectorLength(vtemp);

            // create offset to touch objects far away
            if(distanceOffset < 1)
                distanceOffset = 1;

            // if distance is smaller then the a certain threshold it is assumed to be touched
            if(distance < distanceOffset*0.1)
            {
                touched[i] = true;
            }
            else
                touched[i] = false;
        }

        // set the interaction to all touched objects
        for(int i=0; i<touched.length; i++)
        {
            ObjectHolder.getObjectHolderInstance().changeText(i, null);
        }
    }


    // check point intersection
    public static boolean[] checkObjectPointIntersection(float[] point, float start[])
    {
        // now check intersection device - touchPoint and cube

        // calculate the ray between the device and the point
        float[] vector = new float[3];

        for(int i=0; i<3; i++)
            vector[i] = point[i] - start[i];
        float vectorLength = getVectorLength(vector);

        // retreive the positions of the cubes which are stored in the ObjectHolder
        float[][] cubePositions = ObjectHolder.getObjectHolderInstance().getCubePositions();
        // return for each cube if it has been touched
        boolean[] touched = new boolean[cubePositions.length];

        // check for all cubes : calculate the distance between the cube position and the ray
        for(int j=0; j<cubePositions.length; j++)
        {
            touched[j] = false;
            // cube position - device coords
            //float subCubeDev[] = new float[3];
            //for (int i = 0; i < 3; i++)
              //  subCubeDev[i] = cubePositions[j][i] - start[i];
            float[] subCubeDev = MathUtils.substractVector(cubePositions[j], start);

            float[] crossVec = crossProduct(subCubeDev, vector);
            float crossVecLen = getVectorLength(crossVec);

            float distance = crossVecLen / vectorLength;

            // if the distance is smaller than 1 it is assumed the cube has touched
            if(distance < 0.1)
                touched[j] = true;
        }
        return touched;
    }


}
