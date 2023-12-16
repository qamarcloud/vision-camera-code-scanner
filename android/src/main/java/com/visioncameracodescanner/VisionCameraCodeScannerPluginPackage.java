package com.visioncameracodescanner;

import androidx.annotation.NonNull;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import com.mrousavy.camera.frameprocessor.FrameProcessorPlugin;

import java.util.Collections;
import java.util.List;

import com.mrousavy.camera.frameprocessor.FrameProcessorPluginRegistry;
import com.visioncameracodescanner.VisionCameraCodeScannerPlugin;
import java.util.Map;
import java.util.HashMap;

public class VisionCameraCodeScannerPluginPackage implements ReactPackage {

  static {
    Map<String, Object> data = new HashMap<>();
    data.put("scanCodes", "");
    FrameProcessorPluginRegistry.addFrameProcessorPlugin("scanCodes",
        options -> new VisionCameraCodeScannerPlugin(data));
  }

  @NonNull
  @org.jetbrains.annotations.NotNull
  @Override
  public List<NativeModule> createNativeModules(
      @NonNull @org.jetbrains.annotations.NotNull ReactApplicationContext reactContext) {
    // FrameProcessorPlugin.register(new VisionCameraCodeScannerPlugin());
    return Collections.emptyList();
  }

  @NonNull
  @org.jetbrains.annotations.NotNull
  @Override
  public List<ViewManager> createViewManagers(
      @NonNull @org.jetbrains.annotations.NotNull ReactApplicationContext reactContext) {
    return Collections.emptyList();
  }
}