package com.birgit.projectba.appFunctions;

import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

// class identifies types of user interactions
public class TouchHandler
{
    // gesture detector for identifying the interactions
    private GestureDetector gestureDetector;

    private View view;

    private float touchCoords[] = new float[4];
    private TouchHandlerCallbackIF callback;


    // possible types of interactions that are used in this application
    public enum TOUCH_MODE
    {
        LONG_TOUCH, SWIPE_RIGHT, SWIPE_LEFT, SWIPE_UP, SWIPE_DOWN, TAP
    }


    public TouchHandler(Context context, TouchHandlerCallbackIF callback)
    {
        gestureDetector = new GestureDetector(context, new TouchListener());
        this.callback = callback;
    }

    public void onTouchEvent(MotionEvent motionEvent,  View view)
    {
        this.view = view;
        gestureDetector.onTouchEvent(motionEvent);
    }



    // identifies the type of interaction gesture
    class TouchListener extends GestureDetector.SimpleOnGestureListener
    {

        // swipe interaction
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            TOUCH_MODE touchMode = null;

            float swipeThreshold = 100;
            float distanceX = e2.getX() - e1.getX();
            float distanceY = e2.getY() - e1.getY();

            touchCoords[0] = e1.getX();
            touchCoords[1] = e1.getY();
            touchCoords[2] = e2.getX();
            touchCoords[3] = e2.getY();

            // need to identify which direction
            // check if swipe horizontal
            if(Math.abs(distanceX) > swipeThreshold || Math.abs(distanceY) > swipeThreshold)
            {
                if (Math.abs(distanceX) > Math.abs(distanceY)) {
                    if (distanceX < 0) {
                        touchMode = TOUCH_MODE.SWIPE_LEFT;
                    } else {
                        touchMode = TOUCH_MODE.SWIPE_RIGHT;
                    }
                }
                // swipe is vertical
                else {
                    if (distanceY < 0) {
                        touchMode = TOUCH_MODE.SWIPE_UP;
                    } else {
                        touchMode = TOUCH_MODE.SWIPE_DOWN;
                    }
                }
            }
            // return the result back to main
            callback.touchHandlerCallback(touchMode, touchCoords);
            return true;
        }


        @Override
        public void onLongPress(MotionEvent e)
        {
            float c_x = e.getX() / view.getWidth();
            float c_y = e.getY() / view.getHeight();

            // return the result and touched coordinates back to main
            callback.touchHandlerCallback(TOUCH_MODE.LONG_TOUCH, new float[]{c_x, c_y});
        }


        @Override
        public boolean onDoubleTap(MotionEvent e)
        {
            float c_x = e.getX() / view.getWidth();
            float c_y = e.getY() / view.getHeight();

            callback.touchHandlerCallback(TOUCH_MODE.TAP, new float[]{c_x, c_y});
            return  true;
        }

    }

    // interface to give results to MainActivity
    public interface TouchHandlerCallbackIF
    {
        public void touchHandlerCallback(TOUCH_MODE touchMode, float[] coords);
    }
}
