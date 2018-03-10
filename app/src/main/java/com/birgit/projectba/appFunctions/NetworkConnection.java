package com.birgit.projectba.appFunctions;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.StringRequest;
import com.birgit.projectba.utils.Utils;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;

import java.util.HashMap;
import java.util.Map;


// this class connects to the network and gets the result back
// the class uses the android volley connection library to handle the requests

public class NetworkConnection
{
    // instance of class
    private static NetworkConnection networkConnection;

    // settings for the network connection
    private String url= "http://192.168.156.225:8000";
    private Network network ;
    private Cache cache;
    private RequestQueue requestQueue;
    // interface to notify main class
    private NetworkResponseCallbackIF responseCallback;

    private NetworkConnection(Context context)
    {
        // no multiple instances
        network = new BasicNetwork(new HurlStack());
        cache = new DiskBasedCache(context.getCacheDir(), 1024*1024);
        requestQueue = new RequestQueue(cache, network);
        requestQueue.start();
    }

    // constructor like method --> called by MainActivity as its context needed
    public static NetworkConnection getNetworkConnection(Context context, NetworkResponseCallbackIF callback)
    {
        if(networkConnection == null)
        {
            networkConnection = new NetworkConnection(context);
        }
        networkConnection.setConnection(callback);
        return networkConnection;
    }

    // set up a connection; start the request queue
    private void setConnection(NetworkResponseCallbackIF callbackIF)
    {
        responseCallback = callbackIF;
        requestQueue.start();
    }


    // callback: is called when server return success and reponse
    private Response.Listener responseListener = new Response.Listener<String>()
    {
        @Override
        public void onResponse(String response)
        {
            // returns response to main class
            responseCallback.networkResponseCallback(response);
        }
    };

    // callback: is called when server returns error
    private Response.ErrorListener errorListener = new Response.ErrorListener()
    {
        @Override
        public void onErrorResponse(VolleyError error)
        {
            // returns error to main class
            responseCallback.networkErrorCallback(error.toString());
        }
    };

    // this method sends a post request with a sting attribute to the server
    public void postRequest(final String codeString)
    {
        // corresponding url with string request
        String surl = url+"/answer/code/";

        // declares the request
        StringRequest stringRequest = new StringRequest(Request.Method.POST, surl,
                responseListener, errorListener
        )
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                // parameter is send to server
                params.put("code", codeString);
                return params;
            }

        };

        // the request needs to be added to the queue only; library handles the rest
        requestQueue.add(stringRequest);
    }


    //this method send a post request with an image to the server
    public void postImageRequest(TangoImageBuffer imageBuffer)
    {
        String surl = url+"/answer/image/";
        // the images needs to be encoded to a string
        final byte[] imageData = Utils.getCurrentImageJPEG(imageBuffer);
        final String encodedImage = Base64.encodeToString(imageData, Base64.DEFAULT);

        StringRequest postImageRequest = new StringRequest(Request.Method.POST, surl,
                responseListener, errorListener)
        {
            @Override
            protected Map<String, String> getParams()
            {
                // set the encoded image to the request
                Map<String, String> params = new HashMap<>();
                params.put("image", encodedImage);
                return params;
            }

        };
        // as the image takes longer to process; timeout has to be set higher
        postImageRequest.setRetryPolicy(new DefaultRetryPolicy(50000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(postImageRequest);
    }

    // interface for communication with MainActivity
    public interface NetworkResponseCallbackIF
    {
        public void networkResponseCallback(String response);
        public void networkErrorCallback(String error);
    }
}
