package in.sursandconnect.admin;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.content.ContentValues;
import android.graphics.Insets;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.FrameLayout;

import java.util.Locale;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String APP_URL = "file:///android_asset/admin/index.html";
    private static final String INTERNAL_HOST = "sursandconnect.github.io";
    private static final int REQ_LOCATION = 2001;
    private static final int REQ_CAMERA = 2002;
    private static final int REQ_NOTIFICATION = 2003;
    private static final int REQ_FILE = 2004;
    private static final int REQ_CONTACT = 2005;
    private static final int REQ_CONTACT_PERMISSION = 2006;

    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private String contactTarget;
    private WebView printWebView;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        applySystemBarInsets();

        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);

        createNotificationChannel();
        requestNotificationPermission();
        configureWebView();
        // Obsolete bulk background checker disabled.

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(APP_URL);
    }

    private void applySystemBarInsets() {
        final View root = findViewById(R.id.rootView);
        if (root == null) return;

        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int top = 0;
            int bottom = 0;
            int left = 0;
            int right = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.statusBars() |
                    WindowInsets.Type.navigationBars()
                );
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
            } else {
                top = windowInsets.getSystemWindowInsetTop();
                bottom = windowInsets.getSystemWindowInsetBottom();
                left = windowInsets.getSystemWindowInsetLeft();
                right = windowInsets.getSystemWindowInsetRight();
            }

            view.setPadding(left, top, right, bottom);
            return windowInsets;
        });

        root.requestApplyInsets();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " SursandConnectAndroid/1.0");

        NativeBridge nativeBridge = new NativeBridge();
        webView.addJavascriptInterface(nativeBridge, "SursandNative");
        // Contact picker removed in v61. Other native helpers remain available through SursandNative.

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                injectNativeHelpers();
                // Encourage the existing web service worker to refresh without exposing hosting details.
                view.evaluateJavascript("if(navigator.serviceWorker){navigator.serviceWorker.getRegistrations().then(r=>r.forEach(x=>x.update())).catch(()=>{});}", null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOfflinePage();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    geoOrigin = origin;
                    geoCallback = callback;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(new String[]{res});
                            else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                            return;
                        }
                    }
                    request.deny();
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                launchFileChooser(params);
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> startDownload(url, userAgent, mimetype));
    }

    private boolean handleUri(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

        if ((scheme.equals("http") || scheme.equals("https")) && host.equals(INTERNAL_HOST)) return false;

        // Never expose GitHub / repository destinations to normal users.
        if (host.equals("github.com") || host.endsWith(".github.com")) {
            Toast.makeText(this, "This link is not available in the app.", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (scheme.equals("tel") || scheme.equals("sms") || scheme.equals("mailto") || scheme.equals("geo") || scheme.equals("market") || scheme.equals("intent")) {
            openExternal(uri);
            return true;
        }

        if (scheme.equals("http") || scheme.equals("https")) {
            openExternal(uri);
            return true;
        }
        return false;
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No compatible app is available.", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchFileChooser(WebChromeClient.FileChooserParams params) {
        Intent content = params.createIntent();
        content.addCategory(Intent.CATEGORY_OPENABLE);
        content.setType(params.getAcceptTypes() != null && params.getAcceptTypes().length > 0 && !params.getAcceptTypes()[0].isEmpty() ? params.getAcceptTypes()[0] : "image/*");

        Intent camera = null;
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            try {
                camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                String name = "SursandConnect_" + System.currentTimeMillis() + ".jpg";
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sursand Connect");
                cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (cameraUri != null) camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            } catch (Exception ignored) { camera = null; }
        }

        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        chooser.putExtra(Intent.EXTRA_INTENT, content);
        if (camera != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
        try { startActivityForResult(chooser, REQ_FILE); }
        catch (Exception e) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
            Toast.makeText(this, "Unable to open image picker.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
                else if (cameraUri != null) result = new Uri[]{cameraUri};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            cameraUri = null;
        }
        if (requestCode == REQ_CONTACT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Cursor cursor = null;
                try {
                    cursor = getContentResolver().query(data.getData(),
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        String number = cursor.getString(0);
                        final String target = contactTarget == null ? "" : contactTarget;
                        final String safeNumber = number == null ? "" : number.replace("\\","\\\\").replace("'","\\'");
                        final String safeTarget = target.replace("\\","\\\\").replace("'","\\'");
                        webView.evaluateJavascript(
                            "(function(){if(window.SCReceiveContact)SCReceiveContact('"+safeTarget+"','"+safeNumber+"');if(window.scReceiveContact)scReceiveContact('"+safeTarget+"','"+safeNumber+"');})();",
                            null
                        );
                    }
                } catch (Exception ignored) {
                } finally {
                    if (cursor != null) cursor.close();
                }
            }
            contactTarget = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) launchContactPicker();
            else Toast.makeText(this, "Contacts permission is required to choose a phone number.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (requestCode == REQ_LOCATION && geoCallback != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigin, granted, false);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    private void launchContactPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(intent, REQ_CONTACT);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                startActivityForResult(intent, REQ_CONTACT);
            } catch (Exception ignored) {
                Toast.makeText(this, "Unable to open contacts.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startDownload(String url, String userAgent, String mime) {
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mime);
            req.addRequestHeader("User-Agent", userAgent);
            req.setTitle("Sursand Connect download");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);
            DownloadManager dm = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to start download.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOfflinePage() {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='margin:0;background:#f8fafc;font-family:Arial;color:#263238;display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px'><div style='max-width:420px;text-align:center;background:#fff;border-radius:24px;padding:28px;box-shadow:0 18px 45px rgba(15,23,42,.12)'><div style='width:76px;height:76px;border-radius:24px;background:#f28c28;color:#fff;margin:auto;display:flex;align-items:center;justify-content:center;font-size:27px;font-weight:900'>SC</div><h2>Sursand Connect</h2><p style='color:#667085;line-height:1.6'>You appear to be offline. Previously cached pages remain available when possible.</p><button onclick=\"location.href='" + APP_URL + "'\" style='border:0;border-radius:12px;background:#238b45;color:white;padding:12px 22px;font-weight:800'>Try Again</button></div></body></html>";
        webView.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
    }

    private void injectNativeHelpers() {
        String js =
            "(function(){" +
            "try{" +

            // Native share fallback.
            "if(!navigator.share&&window.SursandNative){" +
              "navigator.share=function(d){" +
                "SursandNative.share((d&&d.title)||'Sursand Connect',(d&&d.text)||'',(d&&d.url)||location.href);" +
                "return Promise.resolve();" +
              "};" +
            "}" +

            // Remove privacy/development explanatory text from the account screen.
            "function cleanAccountPrivacy(){" +
              "try{" +
                "if(!/account\\.html(?:$|[?#])/i.test(location.href))return;" +
                "var phrases=[" +
                  "'account privacy'," +
                  "'privacy description'," +
                  "'your personal information'," +
                  "'personal information is'," +
                  "'we respect your privacy'," +
                  "'your privacy'," +
                  "'data privacy'," +
                  "'privacy and security'" +
                "];" +
                "document.querySelectorAll('p,small,.note,.info,.privacy,.privacy-note,.account-note').forEach(function(el){" +
                  "var t=(el.textContent||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                  "if(!t||t.length>650)return;" +
                  "if(phrases.some(function(p){return t.indexOf(p)!==-1;}))el.remove();" +
                "});" +
              "}catch(e){}" +
            "}" +
            "cleanAccountPrivacy();" +
            "if(document.body){" +
              "new MutationObserver(function(){cleanAccountPrivacy();}).observe(document.body,{childList:true,subtree:true});" +
            "}" +

            "}catch(e){}" +
            "})();";

        webView.evaluateJavascript(js, null);
    }

    public class NativeBridge {
        @JavascriptInterface
        public String postJson(String url, String json) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection)new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type","text/plain;charset=utf-8");
                c.setRequestProperty("Accept","application/json,text/plain,*/*");
                byte[] body=(json==null?"{}":json).getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(body.length);
                try(OutputStream os=c.getOutputStream()){os.write(body);}
                int status=c.getResponseCode();
                InputStream is=status>=400?c.getErrorStream():c.getInputStream();
                if(is==null)return "{\"success\":false,\"error\":\"Empty server response\"}";
                java.io.ByteArrayOutputStream bos=new java.io.ByteArrayOutputStream();
                byte[] buf=new byte[4096];int n;
                while((n=is.read(buf))!=-1)bos.write(buf,0,n);
                is.close();
                return bos.toString("UTF-8");
            } catch(Exception e) {
                String msg=e.getMessage()==null?"Network request failed":e.getMessage().replace("\\","\\\\").replace("\"","\\\"");
                return "{\"success\":false,\"error\":\""+msg+"\"}";
            } finally {if(c!=null)c.disconnect();}
        }

        @JavascriptInterface
        public void share(String title, String text, String url) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                String body = (text == null ? "" : text.trim());
                // Do not surface the internal hosting address when sharing from the app shell.
                if (url != null && !url.contains("github.io")) body = body + (body.isEmpty() ? "" : "\n") + url;
                send.putExtra(Intent.EXTRA_SUBJECT, title == null ? "Sursand Connect" : title);
                send.putExtra(Intent.EXTRA_TEXT, body.isEmpty() ? "Sursand Connect" : body);
                startActivity(Intent.createChooser(send, "Share Sursand Connect"));
            });
        }

        @JavascriptInterface
        public void pickContact(String target) {
            runOnUiThread(() -> {
                contactTarget = target == null ? "" : target;
                // ACTION_PICK grants temporary read access to the selected phone row.
                // Avoid a separate READ_CONTACTS permission dialog, which made the picker unreliable/slow.
                launchContactPicker();
            });
        }

        @JavascriptInterface
        public void printHtml(String html, String title) {
            final String safeHtml = html == null ? "" : html;
            final String job = (title == null || title.trim().isEmpty()) ? "Sursand Connect" : title.trim();
            runOnUiThread(() -> {
                try {
                    printWebView = new WebView(MainActivity.this);
                    WebSettings ps = printWebView.getSettings();
                    ps.setJavaScriptEnabled(true);
                    ps.setLoadsImagesAutomatically(true);
                    printWebView.setWebViewClient(new WebViewClient() {
                        @Override public void onPageFinished(WebView view, String url) {
                            PrintManager pm = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                            if (pm != null) pm.print(job, view.createPrintDocumentAdapter(job), new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait()).build());
                        }
                    });
                    printWebView.loadDataWithBaseURL("file:///android_asset/", safeHtml, "text/html", "UTF-8", null);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to open print preview.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void saveHtmlAsJpeg(String html, String title, int width, int height) {
            final String safeHtml = html == null ? "" : html;
            final String safeTitle = (title == null || title.trim().isEmpty()) ? "Sursand_Connect" : title.replaceAll("[^a-zA-Z0-9._-]+", "_");
            final int outW = width > 0 ? width : 1080;
            final int outH = height > 0 ? height : 1350;
            runOnUiThread(() -> {
                try {
                    WebView jpgView = new WebView(MainActivity.this);
                    jpgView.setBackgroundColor(android.graphics.Color.WHITE);
                    FrameLayout rootView = findViewById(R.id.rootView);
                    FrameLayout.LayoutParams jpgLp = new FrameLayout.LayoutParams(outW, outH);
                    jpgView.setTranslationX(-10000f);
                    rootView.addView(jpgView, jpgLp);
                    jpgView.getSettings().setJavaScriptEnabled(true);
                    jpgView.getSettings().setLoadsImagesAutomatically(true);
                    jpgView.setWebViewClient(new WebViewClient() {
                        @Override public void onPageFinished(WebView view, String url) {
                            view.evaluateJavascript("(function(){var t=document.querySelector('.toolbar');if(t)t.style.display='none';var s=document.querySelector('.stage');if(s){s.style.padding='0';s.style.display='block'}var d=document.querySelector('.design');if(d){d.style.transform='none';d.style.margin='0';d.style.boxShadow='none'}var b=document.querySelector('.fitbox');if(b){b.style.width='auto';b.style.height='auto'}document.documentElement.style.background='#fff';document.body.style.background='#fff';})()", null);
                            view.postDelayed(() -> {
                                try {
                                    int ws=View.MeasureSpec.makeMeasureSpec(outW,View.MeasureSpec.EXACTLY);
                                    int hs=View.MeasureSpec.makeMeasureSpec(outH,View.MeasureSpec.EXACTLY);
                                    view.measure(ws,hs); view.layout(0,0,outW,outH);
                                    Bitmap bmp=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
                                    Canvas canvas=new Canvas(bmp); canvas.drawColor(android.graphics.Color.WHITE); view.draw(canvas);
                                    ContentValues cv=new ContentValues();
                                    cv.put(MediaStore.Images.Media.DISPLAY_NAME,safeTitle+".jpg");
                                    cv.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
                                    if(Build.VERSION.SDK_INT>=29)cv.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/Sursand Connect");
                                    Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
                                    if(uri==null)throw new Exception("Unable to create image");
                                    try(OutputStream os=getContentResolver().openOutputStream(uri)){bmp.compress(Bitmap.CompressFormat.JPEG,95,os);}
                                    bmp.recycle();
                                    if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);
                                    view.destroy();
                                    Toast.makeText(MainActivity.this,"JPG saved to Pictures / Sursand Connect",Toast.LENGTH_LONG).show();
                                } catch(Exception ex){
                                    try{if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);view.destroy();}catch(Exception ignored){}
                                    Toast.makeText(MainActivity.this,"Unable to save JPG: "+ex.getMessage(),Toast.LENGTH_LONG).show();
                                }
                            },700);
                        }
                    });
                    jpgView.loadDataWithBaseURL("file:///android_asset/admin/",safeHtml,"text/html","UTF-8",null);
                } catch(Exception e){Toast.makeText(MainActivity.this,"Unable to prepare JPG.",Toast.LENGTH_SHORT).show();}
            });
        }

        @JavascriptInterface
        public void setAdminToken(String token) {
            getSharedPreferences("sursand_native", Context.MODE_PRIVATE)
                .edit().putString("admin_token", token == null ? "" : token).apply();
        }

        @JavascriptInterface
        public String getAppVersion() { return "1.0.0"; }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("sursand_updates", "Sursand Connect Updates", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Events and announcements from Sursand Connect");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    private void scheduleNativeUpdateChecks() {
        Intent intent = new Intent(this, UpdateCheckReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 4102, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
        long every = 15L * 60L * 1000L; // near-real-time fallback; true instant push requires FCM
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30_000L, every, pi);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }
}
