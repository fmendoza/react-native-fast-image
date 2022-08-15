package com.dylanvann.fastimage;

import android.app.Activity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.views.imagehelper.ImageSource;
import androidx.annotation.Nullable;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.facebook.react.bridge.Promise;
import java.io.File;

class FastImageViewModule extends ReactContextBaseJavaModule {

    private static final String REACT_CLASS = "FastImagePreloaderManager";
    private static final String ERROR_LOAD_FAILED = "ERROR_LOAD_FAILED";
    private int preloaders = 0;

    FastImageViewModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @ReactMethod
    public void createPreloader(Promise promise) {
        promise.resolve(preloaders++);
    }

    @ReactMethod
    public void preload(final int preloaderId, final ReadableArray sources) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                FastImagePreloaderListener preloader = new FastImagePreloaderListener(getReactApplicationContext(), preloaderId, sources.size());
                for (int i = 0; i < sources.size(); i++) {
                    final ReadableMap source = sources.getMap(i);
                    final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);

                    Glide
                            .with(activity.getApplicationContext())
                            .downloadOnly()
                            // This will make this work for remote and local images. e.g.
                            //    - file:///
                            //    - content://
                            //    - res:/
                            //    - android.resource://
                            //    - data:image/png;base64
                            .load(
                                    imageSource.isBase64Resource() ? imageSource.getSource() :
                                    imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl()
                            )
                            .listener(preloader)
                            .apply(FastImageViewConverter.getOptions(activity, imageSource, source))
                            .preload();
                }
            }
        });
    }

    @ReactMethod
    public void clearMemoryCache(final Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Glide.get(activity.getApplicationContext()).clearMemory();
                promise.resolve(null);
            }
        });
    }

    @ReactMethod
    public void clearDiskCache(Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        Glide.get(activity.getApplicationContext()).clearDiskCache();
        promise.resolve(null);
    }

    @ReactMethod
    public void getCachePath(final ReadableMap source, final Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);
                final GlideUrl glideUrl = imageSource.getGlideUrl();

                if (glideUrl == null) {
                    promise.resolve(null);
                    return;
                }

                Glide
                        .with(activity.getApplicationContext())
                        .asFile()
                        .load(glideUrl)
                        .apply(FastImageViewConverter.getOptions(activity, imageSource,  source))
                        .listener(new RequestListener<File>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<File> target, boolean isFirstResource) {
                                promise.reject(ERROR_LOAD_FAILED, e);
                                return false;
                            }
                            @Override
                            public boolean onResourceReady(File resource, Object model, Target<File> target, DataSource dataSource, boolean isFirstResource) {
                                promise.resolve(resource.getAbsolutePath());
                                return false;
                            }
                        })
                        .submit();
            }
        });
    }
}
