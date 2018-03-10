
package com.birgit.projectba.appFunctions;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoInvalidException;


// this class saves an afd in background
public class SaveADF extends AsyncTask<Void, Integer, String>
{
    private SaveADFcallback callback;
    private Tango tango;
    private String adfName;

    public SaveADF(SaveADFcallback callbackListener, Tango tango, String adfName)
    {
        this.callback = callbackListener;
        this.tango = tango;
        this.adfName = adfName;
    }

    public void saveADF()
    {
        this.execute();
    }

    @Override
    protected void onPreExecute() { }


    @Override
    protected synchronized String doInBackground(Void... params)
    {
        String adfUuid = null;
        try
        {
            // saves the environment in adf
            adfUuid = tango.saveAreaDescription();
            // saves the metadata like name
            TangoAreaDescriptionMetaData metadata = tango.loadAreaDescriptionMetaData(adfUuid);
            metadata.set(TangoAreaDescriptionMetaData.KEY_NAME, adfName.getBytes());
            tango.saveAreaDescriptionMetadata(adfUuid, metadata);

        }
        catch (Exception e)
        {
            // if there is an error in saving adf
            adfUuid = null;
        }
        return adfUuid;
    }


    @Override
    protected void onProgressUpdate(Integer... progress) { }


    @Override
    protected void onPostExecute(String adfUuid)
    {
        // return result of saving if successful or error
        if (callback != null)
        {
            if (adfUuid != null)
            {
                callback.onSaveAdfSuccess(adfName, adfUuid);
            }
            else
            {
                callback.onSaveAdfFailed(adfName);
            }
        }
    }

    public interface SaveADFcallback
    {
        void onSaveAdfFailed(String adfName);
        void onSaveAdfSuccess(String adfName, String adfUuid);
    }
}
