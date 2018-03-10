package com.birgit.projectba.graphicHolderClasses;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;

import com.birgit.projectba.R;


public class TextureManager
{
    private static TextureManager textureManager = new TextureManager();

    private static Bitmap bitmap;
    public static Bitmap[] letters;

    private static final int bitmapNumWidth = 10;
    private static final int bitmapNumHeight = 10;



    private TextureManager()
    {
        // singleton constructor
    }

    // former constructor
    public static void initTextureManager(Context context)
    {
        letters = new Bitmap[38];
        loadBitmap(context);
        split();
    }


    private static void loadBitmap(Context context)
    {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = true;
        bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.abc_small, options);
    }

    // split the abc bitmap to single letter bitmaps
    private static void split()
    {
        int b=0, c=0;
        int wStep = bitmap.getWidth() /  bitmapNumWidth;
        int hStep = bitmap.getHeight() /  bitmapNumHeight;

        for(int i=0; i<bitmapNumHeight; i++)
        {
            for(int j=0; j<bitmapNumWidth; j++)
            {
                // add only blanks 0
                // 10 numbers 16-25
                //  26 letters 33-59
                // point 95
                if(c==0 || (c>= 16 && c<=25) || (c >= 33 && c<59) || c==95)
                {
                    letters[b] = Bitmap.createBitmap(bitmap, j * wStep, i * hStep, wStep, hStep);
                    b++;
                }
                c++;
            }
        }
    }



    // returns the string as a bitmap for rendering -> this method is called for rendering
    public static Bitmap combineBitmap(String textstring)
    {
        return combineBitmap(textstring, 12, 12);
    }

    public static Bitmap combineBitmap(String textstring, int letterSizeWidth, int letterSizeHeight)
    {
        // ascii A: 65 - Z: 90
        // 0-9 : 48 - 57
        // point: 46
        // blank: 32?
        char [] text = textstring.toUpperCase().toCharArray();
        int elementWidth = letters[0].getWidth();
        int elementHeight = letters[0].getHeight();

        // the bitmap can only be created using canvas
        Bitmap result = Bitmap.createBitmap(letters[0].getWidth()*letterSizeWidth, letters[0].getHeight()*letterSizeHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        char drawText[] = new char[letterSizeWidth*letterSizeHeight];

        for(int k =0; k<drawText.length; k++)
        {
            if(k<text.length)
            {
                drawText[k]=text[k];
            }
            else
            {
                drawText[k] = ' ';
            }
        }

        int c=0;
        for(int i=0; i<letterSizeHeight; i++)
        {
            for(int j=0; j<letterSizeWidth; j++)
            {
                int value = 0;
                if(drawText[c] != ' ')
                {
                    // number
                    if (drawText[c] == '.' || drawText[c] ==',')
                    {
                        value = 37;
                    }
                    else if(Character.isDigit(drawText[c]))
                    {
                        // 1 -10
                        value = ((int) drawText[c]) - 47;
                    }
                    else // letter
                    {
                        // gets ascii number of char (65-90) and maps it to position
                        // of letter in array
                        value = ((int) drawText[c]) - 54;
                    }
                }
                if(value < 0 || value > 37)
                {

                }
                else
                {
                    canvas.drawBitmap(letters[value], j * elementWidth, i * elementHeight, null);
                    c++;
                }
            }
        }
        canvas = null;
        return result;
    }

}
