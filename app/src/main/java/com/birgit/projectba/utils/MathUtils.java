package com.birgit.projectba.utils;


import android.opengl.Matrix;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

// methods for vector math

public class MathUtils
{
    // calculate the projection matrix from the camera intrinsics
    public static float[] calculateProjectionMatrix(TangoCameraIntrinsics intrinsics)
    {
        float cx = (float) intrinsics.cx;
        float cy = (float) intrinsics.cy;
        float width = (float) intrinsics.width;
        float height = (float) intrinsics.height;
        float fx = (float) intrinsics.fx;
        float fy = (float) intrinsics.fy;

        float near = 0.1f;
        float far = 100;

        float xScale = near / fx;
        float yScale = near / fy;
        float xOffset = (cx - (width / 2.0f)) * xScale;
        float yOffset = -(cy - (height / 2.0f)) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                xScale * (float) -width / 2.0f - xOffset,
                xScale * (float) width / 2.0f - xOffset,
                yScale * (float) -height / 2.0f - yOffset,
                yScale * (float) height / 2.0f - yOffset,
                near, far);
        return m;
    }

    // calculate matrix plane
    public static float[] calculatePlaneTransform(double[] point, double normal[], float[] openGldepth)
    {
        float[] openGlUp = new float[]{0, 1, 0, 0};
        float[] depthOpenGl = new float[16];
        Matrix.invertM(depthOpenGl, 0, openGldepth, 0);
        float[] depthUp = new float[4];
        Matrix.multiplyMV(depthUp, 0, depthOpenGl, 0, openGlUp, 0);

        // calculate the matrix from normal and point
        float[] depthTplane = matrixFromPointNormalUp(point, normal, depthUp);
        float[] openGlTplane = new float[16];
        Matrix.multiplyMM(openGlTplane, 0, openGldepth, 0, depthTplane, 0);
        return openGlTplane;
    }

    // set the matrix
    public static float[] matrixFromPointNormalUp(double[] point, double[] normal, float[] up)
    {
        float[] zAxis = new float[]{(float) normal[0], (float) normal[1], (float) normal[2]};
        normalize(zAxis);
        float[] xAxis = crossProduct(up, zAxis);
        normalize(xAxis);
        float[] yAxis = crossProduct(zAxis, xAxis);
        normalize(yAxis);
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        m[0] = xAxis[0];
        m[1] = xAxis[1];
        m[2] = xAxis[2];
        m[4] = yAxis[0];
        m[5] = yAxis[1];
        m[6] = yAxis[2];
        m[8] = zAxis[0];
        m[9] = zAxis[1];
        m[10] = zAxis[2];
        m[12] = (float) point[0];
        m[13] = (float) point[1];
        m[14] = (float) point[2];
        return m;
    }



    // substract one vector from another
    public static float[] substractVector(float[] v1, float[] v2)
    {
        float[] result = new float[v1.length];
        for(int i=0; i<result.length; i++)
        {
            result[i] = v1[i] - v2[i];
        }
        return result;
    }

    // calculates the crossproduct between two vectors
    public static float[] crossProduct(float[] v1, float[] v2)
    {
        float[] result = new float[3];
        result[0] = v1[1] * v2[2] - v2[1] * v1[2];
        result[1] = v1[2] * v2[0] - v2[2] * v1[0];
        result[2] = v1[0] * v2[1] - v2[0] * v1[1];
        return result;
    }

    public static float[] getRotationInDegrees(float[] rotation)
    {
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        return getRotationInDegrees(quaternion);
    }

    public static float[] getRotationInDegrees(Quaternion quaternion)
    {
        float rx = (float) ((180/Math.PI)* quaternion.getRotationX());
        float ry = (float) ((180/Math.PI)* quaternion.getRotationY());
        float rz = (float) ((180/Math.PI)* quaternion.getRotationZ());

        return new float[]{rx, ry, rz};
    }


    private static void normalize(float[] v)
    {
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= norm;
        v[1] /= norm;
        v[2] /= norm;
    }

    // set vector to normal length
    public static float[] normalizeVector(float[] v)
    {
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= norm;
        v[1] /= norm;
        v[2] /= norm;
        return v;
    }

    public static Vector3 normalizeVector3(Vector3 v)
    {
        double norm = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        v.x /= norm;
        v.y /= norm;
        v.z /= norm;
        return v;
    }

    // returns the length of a vector
    public static float getVectorLength(float[] v)
    {
        double toSqrt = 0;
        for(int i=0; i<v.length; i++)
            toSqrt += Math.pow(v[i], 2);
        double res = Math.sqrt(toSqrt);
        return (float) res;
    }

    // calculates the distance between two points
    public static float distanceBetween2Points(Vector3 p1, Vector3 p2)
    {
        float distance = (float) Math.sqrt(
                Math.pow(p1.x - p2.x, 2) +
                        Math.pow(p1.y - p2.y, 2) +
                        Math.pow(p1.z - p2.z, 2));
        return distance;
    }

    // finds the middle point
    public static Vector3 middleOf2Points(Vector3 p1, Vector3 p2)
    {
        Vector3 middle = new Vector3(
                (p1.x + p2.x) / 2,
                (p1.y + p2.y) / 2,
                (p1.z + p2.z) / 2
                );
        return middle;
    }

    // converts the pose data to transform matrix
    public static float[] poseAsMatrix(TangoPoseData pose)
    {
        Vector3 pos = new Vector3(pose.translation[0], pose.translation[1], pose.translation[2]);
        Quaternion rot = new Quaternion(pose.rotation[3], pose.rotation[0],
                pose.rotation[1], pose.rotation[2]);
       // raawali uses other rotation angle
        rot.conjugate();
        Matrix4 matrix = new Matrix4();
        matrix.setAll(pos, new Vector3(1, 1, 1), rot);
        return matrix.getFloatValues();
    }
}
