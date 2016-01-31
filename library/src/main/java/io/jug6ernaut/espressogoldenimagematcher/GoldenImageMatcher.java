package io.jug6ernaut.espressogoldenimagematcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.View;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Created by williamwebb on 10/16/15.
 */
public class GoldenImageMatcher extends TypeSafeMatcher<View> {

  public static Matcher<View> goldenImage(String path) {
    return new GoldenImageMatcher(path);
  }

  private final String path;
  private       String error;

  public GoldenImageMatcher(String path) {
    this.path = path;
  }

  @Override
  protected boolean matchesSafely(View view) {
    Screenshot screenshot = new Screenshot((Activity) view.getContext());
    Bitmap snap = screenshot.snap();

    try {
      ImageUtils.isSimilar(path, snap);
      return true;
    } catch (ImageUtils.ImageComparisonException e) {
      error = e.getMessage();
      return false;
    }
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("with goldenImage from path: " + path + ". " + error);
  }

  private static class Screenshot {
    private final View view;

    /**
     * Create snapshots based on the view and its children.
     */
    public Screenshot(View root) {
      this.view = root;
    }

    /**
     * Create snapshot handler that captures the root of the whole activity.
     */
    public Screenshot(Activity activity) {
      this.view = activity.getWindow().getDecorView().getRootView();
    }

    /**
     * Take a snapshot of the view.
     */
    public Bitmap snap() {
      view.setDrawingCacheEnabled(true);
      Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
      view.setDrawingCacheEnabled(false);
      return bitmap;
    }
  }
}
