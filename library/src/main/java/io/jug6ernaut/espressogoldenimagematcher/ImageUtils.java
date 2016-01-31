/*
 * Copyright (C) 2014 The Android Open Source Project
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
package io.jug6ernaut.espressogoldenimagematcher;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static android.support.test.espresso.core.deps.guava.base.Preconditions.checkArgument;
import static android.support.test.espresso.core.deps.guava.base.Preconditions.checkNotNull;
import static java.io.File.separatorChar;

/**
 * Utilities related to image processing.
 */
public class ImageUtils {
  /**
   * Normally, this test will fail when there is a missing thumbnail. However, when
   * you create creating a new test, it's useful to be able to turn this off such that
   * you can generate all the missing thumbnails in one go, rather than having to run
   * the test repeatedly to get to each new render assertion generating its thumbnail.
   */
  private static final int    THUMBNAIL_SIZE         = 500;
  private static final double MAX_PERCENT_DIFFERENCE = .7;

  public static void isSimilar(@NonNull String relativePath, @NonNull Bitmap image) throws ImageComparisonException {
    Bitmap goldenImage = BitmapFactory.decodeFile(relativePath);

    isSimilar(image, goldenImage, MAX_PERCENT_DIFFERENCE);

    goldenImage.recycle();
  }

  public static void isSimilar(@NonNull Bitmap toCompare, @NonNull Bitmap goldenImage, double maxDifference) throws ImageComparisonException {
    checkNotNull(toCompare, "Image to compare is null");
    checkNotNull(goldenImage, "Provided Golden Image is null");
    checkArgument(toCompare.getWidth() != goldenImage.getWidth(), "Image width's are not equal");
    checkArgument(toCompare.getHeight() != goldenImage.getHeight(), "Image height's are not equal");

    int maxDimension = Math.max(toCompare.getWidth(), toCompare.getHeight());
    double scale = THUMBNAIL_SIZE / (double) maxDimension;

    int width = (int) (scale * toCompare.getWidth());
    int height = (int) (scale * toCompare.getHeight());

    Bitmap toCompareThumbnail = Bitmap.createScaledBitmap(toCompare, width, height, false);
    Bitmap goldenThumbnail = ThumbnailUtils.extractThumbnail(goldenImage, width, height);

    assertImageSimilar(toCompareThumbnail, goldenThumbnail, maxDifference);

    toCompareThumbnail.recycle();
    goldenThumbnail.recycle();
  }

  public static void assertImageSimilar(Bitmap goldenImage,
                                        Bitmap image,
                                        double maxPercentDifferent) throws ImageComparisonException {

    assertEquals(Config.ARGB_8888, goldenImage.getConfig());
    int imageWidth = Math.min(goldenImage.getWidth(), image.getWidth());
    int imageHeight = Math.min(goldenImage.getHeight(), image.getHeight());
    // Blur the images to account for the scenarios where there are pixel
    // differences
    // in where a sharp edge occurs
    // goldenImage = blur(goldenImage, 6);
    // image = blur(image, 6);
    int width = 3 * imageWidth;
    @SuppressWarnings("UnnecessaryLocalVariable")
    int height = imageHeight; // makes code more readable
    Bitmap deltaImage = Bitmap.createBitmap(width, height, Config.ARGB_8888);

    // Compute delta map
    long delta = 0;
    for (int y = 0; y < imageHeight; y++) {
      for (int x = 0; x < imageWidth; x++) {
        int goldenRgb = goldenImage.getPixel(x, y);
        int rgb = image.getPixel(x, y);
        if (goldenRgb == rgb) {
          deltaImage.setPixel(imageWidth + x, y, 0x00808080);
          continue;
        }
        // If the pixels have no opacity, don't delta colors at all
        if (((goldenRgb & 0xFF000000) == 0) && (rgb & 0xFF000000) == 0) {
          deltaImage.setPixel(imageWidth + x, y, 0x00808080);
          continue;
        }
        int deltaR = ((rgb & 0xFF0000) >>> 16) - ((goldenRgb & 0xFF0000) >>> 16);
        int newR = 128 + deltaR & 0xFF;
        int deltaG = ((rgb & 0x00FF00) >>> 8) - ((goldenRgb & 0x00FF00) >>> 8);
        int newG = 128 + deltaG & 0xFF;
        int deltaB = (rgb & 0x0000FF) - (goldenRgb & 0x0000FF);
        int newB = 128 + deltaB & 0xFF;
        int avgAlpha = ((((goldenRgb & 0xFF000000) >>> 24) + ((rgb & 0xFF000000) >>> 24)) / 2) << 24;
        int newRGB = avgAlpha | newR << 16 | newG << 8 | newB;
        deltaImage.setPixel(imageWidth + x, y, newRGB);
        delta += Math.abs(deltaR);
        delta += Math.abs(deltaG);
        delta += Math.abs(deltaB);
      }
    }
    // 3 different colors, 256 color levels
    long total = imageHeight * imageWidth * 3L * 256L;
    float percentDifference = (float) (delta * 100 / (double) total);

    if (percentDifference > maxPercentDifferent) {
      saveImage("goldenThumbnail", goldenImage);
      saveImage("screenThumbnail", image);
      saveImage("delta", deltaImage);

      fail(String.format("Images differ (by %.1f%%)", percentDifference));
    }
    deltaImage.recycle();
  }

  private static void assertEquals(Object one, Object two) throws ImageComparisonException {
    if (!one.equals(two)) {
      throw new ImageComparisonException("");
    }
  }

  private static void fail(String message) throws ImageComparisonException {
    throw new ImageComparisonException(message);
  }

  public static class ImageComparisonException extends Exception {
    public ImageComparisonException(String message) {
      super(message);
    }

    public ImageComparisonException(Exception e) {
      super(e);
    }
  }

  private static String getName(@NonNull String relativePath) {
    return relativePath.substring(relativePath.lastIndexOf(separatorChar) + 1);
  }

  private static void saveImage(String path, Bitmap image) throws ImageComparisonException {
    try {
      String base = Environment.getExternalStorageDirectory().toString() + "/TEST/";
      OutputStream fOut = null;
      File file = new File(base, path + ".jpg"); // the File to save to
      fOut = new FileOutputStream(file);

      image.compress(Bitmap.CompressFormat.JPEG, 85, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
      fOut.flush();
      fOut.close(); // do not forget to close the stream
    } catch (IOException ioe) {
      throw new ImageComparisonException(ioe);
    }
  }

  public static Bitmap decodeSampledBitmapFromResource(String path, int reqWidth, int reqHeight) {

    // First decode with inJustDecodeBounds=true to check dimensions
    final Options options = new Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(path, options);

    // Calculate inSampleSize
    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

    // Decode bitmap with inSampleSize set
    options.inJustDecodeBounds = false;
    return BitmapFactory.decodeFile(path, options);
  }

  public static int calculateInSampleSize(Options options, int reqWidth, int reqHeight) {
    // Raw height and width of image
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {

      final int halfHeight = height / 2;
      final int halfWidth = width / 2;

      // Calculate the largest inSampleSize value that is a power of 2 and keeps both
      // height and width larger than the requested height and width.
      while ((halfHeight / inSampleSize) > reqHeight
          && (halfWidth / inSampleSize) > reqWidth) {
        inSampleSize *= 2;
      }
    }

    return inSampleSize;
  }

  public int getStatusBarHeight(Context context) {
    int result = 0;
    int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
      result = context.getResources().getDimensionPixelSize(resourceId);
    }
    return result;
  }

  private int getNavBarHeight(Context context) {
    Resources resources = context.getResources();
    int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
    if (resourceId > 0) {
      return resources.getDimensionPixelSize(resourceId);
    }
    return 0;
  }
}