package com.etabm.ezypdf;

import android.Manifest;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 701;
    private static final int CAMERA_PERMISSION_REQUEST = 702;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraOutputUri;
    private PermissionRequest pendingCameraRequest;

    private static final String HOME_URL = "https://ezypdf-app.pages.dev/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setStatusBarColor(Color.rgb(25, 18, 48));
        getWindow().setNavigationBarColor(Color.BLACK);

        // XML layout එක සම්බන්ධ කිරීම (Status bar overlap වීම වැළැක්වීමට)
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.addJavascriptInterface(new PdfBridge(), "EzyPdfAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.equals("ezypdf-app.pages.dev") ||
                        host.endsWith(".ezypdf-app.pages.dev"))) {
                    return false;
                }
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                    return true;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installBlobDownloadHandler();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                
                // Camera පර්මිෂන් එක පරීක්ෂා කර popup එක ගෙන්නීම
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
                } else {
                    openImagePicker(params);
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingCameraRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingCameraRequest == request) pendingCameraRequest = null;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null) {
                if (url.startsWith("blob:")) return;
                Toast.makeText(this, "Downloading PDF...", Toast.LENGTH_SHORT).show();
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "Unable to open download link.", Toast.LENGTH_LONG).show();
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera Permission Granted", Toast.LENGTH_SHORT).show();
                if (pendingCameraRequest != null) {
                    PermissionRequest req = pendingCameraRequest;
                    pendingCameraRequest = null;
                    req.grant(req.getResources());
                } else if (filePathCallback != null) {
                    openImagePicker(null);
                }
            } else {
                if (pendingCameraRequest != null) {
                    pendingCameraRequest.deny();
                    pendingCameraRequest = null;
                }
                Toast.makeText(this, "Camera permission is required to capture photos.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openImagePicker(WebChromeClient.FileChooserParams params) {
        Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        gallery.setType("image/*");
        if (params != null) {
            gallery.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                    params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);
        }

        Intent chooser = Intent.createChooser(gallery, "Choose images");
        ArrayList<Intent> extras = new ArrayList<>();

        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            File photo = File.createTempFile("ezypdf_camera_", ".jpg", dir);
            cameraOutputUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);

            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (camera.resolveActivity(getPackageManager()) != null) {
                extras.add(camera);
            }
        } catch (Exception ignored) {
            cameraOutputUri = null;
        }

        if (!extras.isEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toArray(new Intent[0]));
        }

        try {
            startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
        } catch (ActivityNotFoundException e) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = null;
            Toast.makeText(this, "No image picker available.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (cameraOutputUri != null) {
                    results = new Uri[]{cameraOutputUri};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            cameraOutputUri = null;
        }
    }

    private void installBlobDownloadHandler() {
        String js = "(function(){"
                + "if(window.__ezyPdfDownloadInstalled)return;"
                + "window.__ezyPdfDownloadInstalled=true;"
                + "var originalCreateObjectURL = URL.createObjectURL;"
                + "URL.createObjectURL = function(blob) {"
                + "var url = originalCreateObjectURL.apply(this, arguments);"
                + "if(blob){"
                + "var reader = new FileReader();"
                + "reader.onloadend = function() {"
                + "try{ EzyPdfAndroid.savePdf(reader.result, 'EzyPdf_Document.pdf'); }catch(e){}"
                + "};"
                + "reader.readAsDataURL(blob);"
                + "}"
                + "return url;"
                + "};"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private class PdfBridge {
        @android.webkit.JavascriptInterface
        public void savePdf(String dataUrl, String filename) {
            runOnUiThread(() -> savePdfToDownloads(dataUrl, filename));
        }
    }

    private void savePdfToDownloads(String dataUrl, String filename) {
        try {
            if (dataUrl == null || !dataUrl.contains(",")) {
                throw new IllegalArgumentException("Invalid PDF data");
            }
            String base64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            String safeName = (filename == null ? "EzyPdf_Document.pdf" :
                    filename.replaceAll("[\\\\/:*?\"<>|]", "_"));
            if (!safeName.toLowerCase().endsWith(".pdf")) safeName += ".pdf";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri item = getContentResolver().insert(collection, values);
            if (item == null) throw new Exception("Could not create download");

            try (OutputStream out = getContentResolver().openOutputStream(item)) {
                if (out == null) throw new Exception("Could not open download");
                out.write(bytes);
                out.flush();
            }
            Toast.makeText(this, "PDF saved to Downloads folder!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "PDF save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (pendingCameraRequest != null) { pendingCameraRequest.deny(); pendingCameraRequest = null; }
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("EzyPdfAndroid");
            webView.destroy();
        }
        super.onDestroy();
    }
}
