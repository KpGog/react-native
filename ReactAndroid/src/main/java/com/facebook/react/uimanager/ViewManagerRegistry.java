/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.uimanager;

import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import androidx.annotation.Nullable;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.common.MapBuilder;
import java.util.List;
import java.util.Map;

/**
 * Class that stores the mapping between native view name used in JS and the corresponding instance
 * of {@link ViewManager}.
 */
public final class ViewManagerRegistry {

  private final Map<String, ViewManager> mViewManagers;
  private final @Nullable ViewManagerResolver mViewManagerResolver;
  private final MemoryTrimCallback mMemoryTrimCallback = new MemoryTrimCallback();

  public ViewManagerRegistry(ViewManagerResolver viewManagerResolver) {
    mViewManagers = MapBuilder.newHashMap();
    mViewManagerResolver = viewManagerResolver;
  }

  public ViewManagerRegistry(List<ViewManager> viewManagerList) {
    Map<String, ViewManager> viewManagerMap = MapBuilder.newHashMap();
    for (ViewManager viewManager : viewManagerList) {
      viewManagerMap.put(viewManager.getName(), viewManager);
    }

    mViewManagers = viewManagerMap;
    mViewManagerResolver = null;
  }

  public ViewManagerRegistry(Map<String, ViewManager> viewManagerMap) {
    mViewManagers =
        viewManagerMap != null ? viewManagerMap : MapBuilder.<String, ViewManager>newHashMap();
    mViewManagerResolver = null;
  }

  /**
   * Trim View Recycling memory aggressively. Whenever the system is running even slightly low on
   * memory or is backgrounded, we immediately flush all recyclable Views. GC and memory swaps cause
   * intense CPU pressure, so we always favor low memory usage over View recycling, even if there is
   * only "moderate" pressure.
   */
  private class MemoryTrimCallback implements ComponentCallbacks2 {
    @Override
    public void onTrimMemory(int level) {
      Runnable runnable =
          new Runnable() {
            @Override
            public void run() {
              for (Map.Entry<String, ViewManager> entry : mViewManagers.entrySet()) {
                entry.getValue().trimMemory();
              }
            }
          };
      if (UiThreadUtil.isOnUiThread()) {
        runnable.run();
      } else {
        UiThreadUtil.runOnUiThread(runnable);
      }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {}

    @Override
    public void onLowMemory() {
      this.onTrimMemory(0);
    }
  }

  /**
   * @param className {@link String} that identifies the {@link ViewManager} inside the {@link
   *     ViewManagerRegistry}. This methods {@throws IllegalViewOperationException} if there is no
   *     view manager registered for the className received as a parameter.
   * @return the {@link ViewManager} registered to the className received as a parameter
   */
  public ViewManager get(String className) {
    ViewManager viewManager = mViewManagers.get(className);
    if (viewManager != null) {
      return viewManager;
    }
    if (mViewManagerResolver != null) {
      viewManager = getViewManagerFromResolver(className);
      if (viewManager != null) return viewManager;
      throw new IllegalViewOperationException(
          "ViewManagerResolver returned null for "
              + className
              + ", existing names are: "
              + mViewManagerResolver.getViewManagerNames());
    }
    throw new IllegalViewOperationException("No ViewManager found for class " + className);
  }

  private @Nullable ViewManager getViewManagerFromResolver(String className) {
    @Nullable ViewManager viewManager;
    viewManager = mViewManagerResolver.getViewManager(className);
    if (viewManager != null) {
      mViewManagers.put(className, viewManager);
    }
    return viewManager;
  }

  /**
   * @param className {@link String} that identifies the {@link ViewManager} inside the {@link
   *     ViewManagerRegistry}.
   * @return the {@link ViewManager} registered to the className received as a parameter or null if
   *     there is no ViewManager associated to the className received as a parameter.
   */
  @Nullable
  ViewManager getViewManagerIfExists(String className) {
    ViewManager viewManager = mViewManagers.get(className);
    if (viewManager != null) {
      return viewManager;
    }
    if (mViewManagerResolver != null) {
      return getViewManagerFromResolver(className);
    }
    return null;
  }

  /** Send lifecycle signal to all ViewManagers that StopSurface has been called. */
  public void onSurfaceStopped(int surfaceId) {
    Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            for (Map.Entry<String, ViewManager> entry : mViewManagers.entrySet()) {
              entry.getValue().onSurfaceStopped(surfaceId);
            }
          }
        };
    if (UiThreadUtil.isOnUiThread()) {
      runnable.run();
    } else {
      UiThreadUtil.runOnUiThread(runnable);
    }
  }
}
