/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mkl.launcher;

import java.util.Random;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
//import android.graphics.TableMaskFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.util.DisplayMetrics;

/**
 * Various utilities shared amongst the Launcher's classes.
 */
final class Utilities {
    private static final String TAG = "Launcher.Utilities";

    private static int sIconWidth = -1;
    private static int sIconHeight = -1;
    private static int sIconTextureWidth = -1;
    private static int sIconTextureHeight = -1;

    private static final Paint sBlurPaint = new Paint();
    private static final Paint sGlowColorPressedPaint = new Paint();
    private static final Paint sGlowColorFocusedPaint = new Paint();
    private static final Paint sDisabledPaint = new Paint();
    private static final Rect sOldBounds = new Rect();
    private static final Canvas sCanvas = new Canvas();

    static {
        sCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                Paint.FILTER_BITMAP_FLAG));
    }
    static int sColors[] = { 0xffff0000, 0xff00ff00, 0xff0000ff };
    static int sColorIndex = 0;

    /**
     * Returns a bitmap suitable for the all apps view. Used to convert pre-ICS
     * icon bitmaps that are stored in the database (which were 74x74 pixels at hdpi size)
     * to the proper size (48dp)
     */
    static Bitmap createIconBitmap(Bitmap icon, Context context) {
        int textureWidth = sIconTextureWidth;
        int textureHeight = sIconTextureHeight;
        int sourceWidth = icon.getWidth();
        int sourceHeight = icon.getHeight();
        if (sourceWidth > textureWidth && sourceHeight > textureHeight) {
            // Icon is bigger than it should be; clip it (solves the GB->ICS migration case)
            return Bitmap.createBitmap(icon,
                    (sourceWidth - textureWidth) / 2,
                    (sourceHeight - textureHeight) / 2,
                    textureWidth, textureHeight);
        } else if (sourceWidth == textureWidth && sourceHeight == textureHeight) {
            // Icon is the right size, no need to change it
            return icon;
        } else {
            // Icon is too small, render to a larger bitmap
            return createIconBitmap(new BitmapDrawable(icon), context);
        }
    }

    /**
     * Returns a bitmap suitable for the all apps view.
     */
    static Bitmap createIconBitmap(Drawable icon, Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }

            int width = sIconWidth;
            int height = sIconHeight;

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }
            int sourceWidth = icon.getIntrinsicWidth();
            int sourceHeight = icon.getIntrinsicHeight();

            if (sourceWidth > 0 && sourceHeight > 0) {
                // There are intrinsic sizes.
                if (width < sourceWidth || height < sourceHeight) {
                    // It's too big, scale it down.
                    final float ratio = (float) sourceWidth / sourceHeight;
                    if (sourceWidth > sourceHeight) {
                        height = (int) (width / ratio);
                    } else if (sourceHeight > sourceWidth) {
                        width = (int) (height * ratio);
                    }
                } else if (sourceWidth < width && sourceHeight < height) {
                    // Don't scale up the icon
                    width = sourceWidth;
                    height = sourceHeight;
                }
            }

            // no intrinsic size --> use default size
            int textureWidth = sIconTextureWidth;
            int textureHeight = sIconTextureHeight;

            final Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            final int left = (textureWidth-width) / 2;
            final int top = (textureHeight-height) / 2;

            if (false) {
                // draw a big box for the icon for debugging
                canvas.drawColor(sColors[sColorIndex]);
                if (++sColorIndex >= sColors.length) sColorIndex = 0;
                Paint debugPaint = new Paint();
                debugPaint.setColor(0xffcccc00);
                canvas.drawRect(left, top, left+width, top+height, debugPaint);
            }

            sOldBounds.set(icon.getBounds());
            icon.setBounds(left, top, left+width, top+height);
            icon.draw(canvas);
            icon.setBounds(sOldBounds);
            canvas.setBitmap(null);

            return bitmap;
        }
    }

    static void drawSelectedAllAppsBitmap(Canvas dest, int destWidth, int destHeight,
            boolean pressed, Bitmap src) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                // We can't have gotten to here without src being initialized, which
                // comes from this file already.  So just assert.
                //initStatics(context);
                throw new RuntimeException("Assertion failed: Utilities not initialized");
            }

            dest.drawColor(0, PorterDuff.Mode.CLEAR);

            int[] xy = new int[2];
            Bitmap mask = src.extractAlpha(sBlurPaint, xy);

            float px = (destWidth - src.getWidth()) / 2;
            float py = (destHeight - src.getHeight()) / 2;
            dest.drawBitmap(mask, px + xy[0], py + xy[1],
                    pressed ? sGlowColorPressedPaint : sGlowColorFocusedPaint);

            mask.recycle();
        }
    }

    /**
     * Returns a Bitmap representing the thumbnail of the specified Bitmap.
     * The size of the thumbnail is defined by the dimension
     * android.R.dimen.launcher_application_icon_size.
     *
     * @param bitmap The bitmap to get a thumbnail of.
     * @param context The application's context.
     *
     * @return A thumbnail for the specified bitmap or the bitmap itself if the
     *         thumbnail could not be created.
     */
    static Bitmap resampleIconBitmap(Bitmap bitmap, Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }

            if (bitmap.getWidth() == sIconWidth && bitmap.getHeight() == sIconHeight) {
                return bitmap;
            } else {
                return createIconBitmap(new BitmapDrawable(bitmap), context);
            }
        }
    }

    static Bitmap drawDisabledBitmap(Bitmap bitmap, Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }
            final Bitmap disabled = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(disabled);
            
            canvas.drawBitmap(bitmap, 0.0f, 0.0f, sDisabledPaint);

            canvas.setBitmap(null);

            return disabled;
        }
    }

    private static void initStatics(Context context) {
        final Resources resources = context.getResources();
        final DisplayMetrics metrics = resources.getDisplayMetrics();
        final float density = metrics.density;

        sIconWidth = sIconHeight = (int) resources.getDimension(R.dimen.app_icon_size);
        sIconTextureWidth = sIconTextureHeight = sIconWidth;

        sBlurPaint.setMaskFilter(new BlurMaskFilter(5 * density, BlurMaskFilter.Blur.NORMAL));
        sGlowColorPressedPaint.setColor(0xffffc300);
//        sGlowColorPressedPaint.setMaskFilter(TableMaskFilter.CreateClipTable(0, 30));
        sGlowColorFocusedPaint.setColor(0xffff8e00);
//        sGlowColorFocusedPaint.setMaskFilter(TableMaskFilter.CreateClipTable(0, 30));

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.2f);
        sDisabledPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        sDisabledPaint.setAlpha(0x88);
    }
    /*
  //圆角的实现
    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap,float roundPx){
    	
    	Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap
			.getHeight(), Config.ARGB_8888);
//    	Text text=res.loadLabel(mPackageManager).toString();
    	Canvas canvas = new Canvas(output);

    	final int color = 0xff424242;
    	final Paint paint = new Paint();
    	final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
    	final RectF rectF = new RectF(rect);
    	//设置图片抗锯齿标志
    	paint.setAntiAlias(true);
    	canvas.drawARGB(0, 0, 0, 0);
    	paint.setColor(color);
    	canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

    	paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
    	canvas.drawBitmap(bitmap, rect, rect, paint);

    	return output;
	}
    
  //获得带倒影的图片和设置透明度的方法 add by chengbin
	public static Bitmap createReflectionImageWithAlpha(Bitmap bitmap,int Alpbanum)
	{
		int[] argb = new int[bitmap.getWidth() * bitmap.getHeight()];
		bitmap.getPixels(argb, 0, bitmap.getWidth(), 0, 0,bitmap.getWidth(), bitmap.getHeight());
		
		//final int reflectionGap = 4;
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		
		// 获得图片的ARGB值 
		Alpbanum = Alpbanum * 255/100; 
    	for (int i = 0; i < argb.length; i++) 
    	{
    		if(argb[i]!=0){
            argb[i] = (Alpbanum << 24) | (argb[i] & 0x00FFFFFF);
            }
    		else{argb[i]=0x00000000;}
        }
    	bitmap = Bitmap.createBitmap(argb, bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
    	
		Matrix matrix = new Matrix();
		matrix.preScale(1, -1);
		
		Bitmap reflectionImage = Bitmap.createBitmap(bitmap, 
				0, height/2, width, height/2, matrix, false);
		
		Bitmap bitmapWithReflection = Bitmap.createBitmap(width, (height + height/2), Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmapWithReflection);
		canvas.drawBitmap(reflectionImage, 0, height, null);
		//canvas.drawRoundRect(0,0,bitmap.getWidth(),bitmap.getHeight());
		Paint deafalutPaint = new Paint();
		canvas.drawRect(0, height,width,height ,
				deafalutPaint);
		getRoundedCornerBitmap(reflectionImage,25.0f);
		canvas.drawBitmap(reflectionImage, 0, height, null);
		
		Paint paint = new Paint();
		LinearGradient shader = new LinearGradient(0,
				bitmap.getHeight(), 0, bitmapWithReflection.getHeight()
				, 0x70ffffff, 0x00ffffff, TileMode.CLAMP);
		paint.setShader(shader);
		// Set the Transfer mode to be porter duff and destination in
		paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
		// Draw a rectangle using the paint with our linear gradient
		canvas.drawRect(0, height, width, bitmapWithReflection.getHeight()
				, paint);
 
		return bitmapWithReflection;
	}
	*/
	static Drawable drawReflection(Drawable icon,Context context){
    	final Resources resources=context.getResources();
    	sIconWidth = sIconHeight = (int) resources.getDimensionPixelSize(R.dimen.app_icon_size);
    	//The gap we want between the reflection and the original image
        final float scale=1.30f;
      
        int width = sIconWidth;
        int height = sIconHeight;
        float ratio=(float)1.0;
        Bitmap original;
        try{
            original= Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return icon;
        }
        final Canvas cv = new Canvas();
        cv.setBitmap(original);
        icon.setBounds(0,0, width, height);
        icon.draw(cv);
        //This will not scale but will flip on the Y axis
        Matrix matrix = new Matrix();
        matrix.preScale(1, -1);
        
        //Create a Bitmap with the flip matix applied to it.
        //We only want the bottom half of the image
        Bitmap reflectionImage;
        try{
            reflectionImage= Bitmap.createBitmap(original, 0, height/2, width, height/2, matrix, false);            
        } catch (OutOfMemoryError e) {
            return new FastBitmapDrawable(original);
        }

        //Create a new bitmap with same width but taller to fit reflection
        Bitmap bitmapWithReflection;
        try{
            bitmapWithReflection= Bitmap.createBitmap(width 
          , (int) (height*scale), Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return new FastBitmapDrawable(original);
        }
      
       //Create a new Canvas with the bitmap that's big enough for
       //the image plus gap plus reflection
       Canvas canvas = new Canvas(bitmapWithReflection);
       //Draw in the gap
       //Paint deafaultPaint = new Paint();
       //canvas.drawRect(0, height, width, height + reflectionGap, deafaultPaint);
       //Draw in the reflection
       canvas.drawBitmap(reflectionImage,0, height-6, null);
       
       //Create a shader that is a linear gradient that covers the reflection
       Paint paint = new Paint(); 
       LinearGradient shader = new LinearGradient(0, original.getHeight(), 0, 
         bitmapWithReflection.getHeight(), 0x70ffffff, 0x00ffffff, 
         TileMode.CLAMP); 
       //Set the paint to use this shader (linear gradient)
       paint.setShader(shader); 
       //Set the Transfer mode to be porter duff and destination in
       paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN)); 
       //Draw a rectangle using the paint with our linear gradient
       canvas.drawRect(0, height-6, width, 
         bitmapWithReflection.getHeight(), paint); 
       //Draw in the original image
       canvas.drawBitmap(original, 0, 0, null);
       original.recycle();
       reflectionImage.recycle();
       try{
           return new FastBitmapDrawable(Bitmap.createScaledBitmap(bitmapWithReflection,Math.round((float)sIconWidth*ratio),(int)(sIconHeight*scale),true));
       }catch(OutOfMemoryError e){
           return icon;
       }
    }

    /** Only works for positive numbers. */
    static int roundToPow2(int n) {
        int orig = n;
        n >>= 1;
        int mask = 0x8000000;
        while (mask != 0 && (n & mask) == 0) {
            mask >>= 1;
        }
        while (mask != 0) {
            n |= mask;
            mask >>= 1;
        }
        n += 1;
        if (n != orig) {
            n <<= 1;
        }
        return n;
    }

    static int generateRandomId() {
        return new Random(System.currentTimeMillis()).nextInt(1 << 24);
    }
}
