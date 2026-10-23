package cn.wengmang.qiping;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android 9+ native launcher, with a local phone uploader and vendored QR encoder. */
public final class MainActivity extends Activity {
    private static final int HOME = 0, APPS = 1, SETTINGS = 2, STARTUP = 3, DEVICE = 4, UPDATES = 5, RECENT = 6, COLLECTION = 7, PAGE_SIZE = GridNavigation.PAGE_SIZE;
    private static final int TEXT = GlassPanel.TEXT;
    private final Handler handler = new Handler();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<AppEntry> apps = new ArrayList<AppEntry>();
    private final Map<String, View> controls = new HashMap<String, View>();
    private final List<List<View>> focusRows = new ArrayList<List<View>>();
    private final LinkedHashMap<String, Bitmap> icons = new LinkedHashMap<String, Bitmap>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> item) {
            return size() > (performance == null ? 16 : performance.iconCount);
        }
    };
    private WorkspaceLayout workspace;
    private WallpaperStore wallpapers;
    private final ExperiencePolicy experience=new ExperiencePolicy();
    private View launchFeedback;
    private GlassPanel launchPanel;
    private IdleClockView idleView;
    private String idleReturnFocus;
    private long lastInput;
    private int wakeKey,wallpaperScene=-1;
    private boolean wakeTouch,wallpaperBusy;
    private volatile boolean wallpaperCancelled;
    private final ExecutorService wallpaperWorker=Executors.newSingleThreadExecutor();
    private static final int WALLPAPER_IMPORT=703,DIAGNOSTIC_EXPORT=704;
    private RunDiagnostics diagnostics;
    private String lastDiagnosticStartup="",pendingDiagnosticReport;
    private boolean scanRunning,scanAgain;
    private long lastMemorySample,lastUpdateTick;
    private final Runnable diagnosticTick=new Runnable(){public void run(){if(!resumed||destroyed)return;sampleMemory(false);handler.postDelayed(this,300000);}};
    private final Runnable launchTimeout=new Runnable(){public void run(){clearLaunchFeedback();scheduleIdle();}};
    private final Runnable idleCheck=new Runnable(){public void run(){
        if(!idleEligible()||workspace.saverMinutes()==0)return;
        long remaining=ExperiencePolicy.remaining(SystemClock.elapsedRealtime(),lastInput,workspace.saverMinutes());
        if(remaining==0)enterScreensaver();else handler.postDelayed(this,remaining);
    }};
    private final Runnable idleClock=new Runnable(){public void run(){if(idleView!=null&&resumed){idleView.invalidate();handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}}};
    private SharedPreferences preferences;
    private SharedPreferences startupPreferences;
    private StartupState startupState;
    private StartupFlow startupFlow;
    private WakeMonitor wakeMonitor;
    private WakeState wakeState;
    private WakeFlow wakeFlow;
    private boolean wakeCountdown;
    private final WakeMonitor.Listener powerListener=new WakeMonitor.Listener(){public void powerChanged(boolean interactive){
        if(destroyed||startupState==null)return;
        pauseStartup();startupNextScanAt=0;startupScanRetries=0;
        if(!interactive)startupState.skip(BootSession.current(MainActivity.this),"进入待机，本次系统重启自动打开已跳过",SystemClock.elapsedRealtime());
        checkStartup();
    }};
    private boolean resumed, pickingForStartup, cancelBackUp;
    private AppEntry startupTarget;
    private long startupDeadline;
    private long startupNextScanAt;
    private int startupScanRetries;
    private TextView startupHint;
    private Favorites favorites;
    private ShortcutStore shortcuts;
    private AppOrganizer organizer;
    private ConfigStore configStore;
    private ParentalStore parental;
    private boolean parentAuthorized,parentFlow,childTimeOpen;
    private int parentEpoch;
    private final ExecutorService parentWorker=Executors.newSingleThreadExecutor();
    private String pendingBackupName,configRecoveryError;
    private boolean configApplying;
    private int configGeneration;
    private static final int CONFIG_EXPORT=701,CONFIG_IMPORT=702;
    private boolean pickingCollection;
    private int collectionOriginPage;
    private String collectionOriginFocus="app0", pickerOriginFocus="collection_add";
    private int unavailableCount;
    private FrameLayout surface;
    private View activeLayer;
    private SceneView scene;
    private TextView heroTitle, heroSubtitle;
    private IconButton networkIcon;
    private final DockTile[] dockTiles = new DockTile[5];
    private int selected = 0;
    private boolean motion;
    private static final String[] DESCRIPTIONS = {"打开影视应用", "打开直播应用", "打开音乐应用", "浏览照片与视频", "查看已安装的应用"};
    private Dialog currentDialog;
    private int fontMode;
    private int quickReturnScreen=-1,quickOrigin=HOME,quickOriginPage,pendingQuickPage=-1;
    private String quickOriginFocus="home_settings",quickReturnFocus="quick_all",pendingQuickFocus;
    private boolean pendingQuickStandalone;
    private boolean refreshAfterDialog;
    private TextView clockText, dateText, networkText;
    private float unit = 1;
    private int screen = HOME, page = 0, pickingSlot = -1;
    private int scanGeneration = 0, nextId = 1;
    private boolean loading = true, scanError = false, destroyed = false;
    private String focusedKey = "";
    private boolean largeText;
    private DeviceInfo deviceInfo;
    private PerformancePolicy performance;
    private boolean uiNeedsRefresh;
    private String launcherVersion="未读取";
    private UpdateRepository updates;
    private final List<AppEntry> gridApps=new ArrayList<AppEntry>();
    private final String[] rememberedFocus=new String[8];
    private TextView gridDetail;
    private boolean focusReady;
    private int heldPageDirection,confirmKey,updatesOrigin=APPS,normalAppsPage,settingsOrigin=HOME,settingsOriginPage;
    private String updatesOriginFocus="",settingsOriginFocus="home_settings";
    private View confirmTarget;
    private boolean confirmLong;
    private final Runnable longConfirm=new Runnable(){@Override public void run(){
        if(confirmTarget!=null&&focusReady&&resumed&&hasWindowFocus()&&getCurrentFocus()==confirmTarget&&confirmTarget.isLongClickable()) {
            confirmLong=true;confirmTarget.setPressed(false);confirmTarget.performLongClick();
        }
    }};
    private final Runnable updateUi=new Runnable(){@Override public void run(){applyUpdatesToUi();}};
    private boolean packageReceiverRegistered;
    private final Runnable packageRefresh=new Runnable(){@Override public void run(){
        if(!resumed||destroyed)return;
        if(confirmTarget!=null){handler.postDelayed(this,180);return;}
        refreshApps();
    }};
    private final BroadcastReceiver packageReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())&&intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
        handler.removeCallbacks(packageRefresh);handler.postDelayed(packageRefresh,400);
    }};

    private final SharedPreferences.OnSharedPreferenceChangeListener bootListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (BootSession.BROADCAST_TOKEN.equals(key)) handler.post(new Runnable() {
                @Override public void run() { checkStartup(); }
            });
        }
    };

    private final Runnable startupTick = new Runnable() {
        @Override public void run() { checkStartup(); }
    };

    private static final class Tag {
        final String key;
        final AppEntry entry;
        final int slot;
        Tag(String key, AppEntry entry, int slot) {
            this.key = key; this.entry = entry; this.slot = slot;
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if(!resumed||destroyed)return;
            updateClock();updateChildHome();
            if(updates!=null&&SystemClock.elapsedRealtime()-lastUpdateTick>=30000){lastUpdateTick=SystemClock.elapsedRealtime();updates.check(false);applyUpdatesToUi();}
            handler.postDelayed(this, childMode()?1000:30000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        preferences = getSharedPreferences("desktop", MODE_PRIVATE);
        parental=new ParentalStore(getSharedPreferences(ParentalStore.PREFS,MODE_PRIVATE));
        workspace=new WorkspaceLayout(preferences,getSharedPreferences("app_organizer",MODE_PRIVATE));wallpapers=new WallpaperStore(this);lastInput=SystemClock.elapsedRealtime();
        configStore=new ConfigStore(preferences,getSharedPreferences("app_organizer",MODE_PRIVATE),new File(getFilesDir(),"config-backups"));
        try{if(configStore.recover())toast("上次恢复未完成，已还原原配置");}catch(IOException error){configRecoveryError=error.getMessage();}
        try{launcherVersion=DeviceInfo.value(getPackageManager().getPackageInfo(getPackageName(),0).versionName);}catch(Exception ignored){}
        diagnostics=RunDiagnostics.get(this);diagnostics.event("桌面创建","版本 "+launcherVersion+" · "+(isHomeIntent(getIntent())?"系统桌面入口":"普通应用入口"));
        deviceInfo = DeviceInfo.read(this);
        startupPreferences = BootSession.preferences(this);
        startupState = new StartupState(startupPreferences);
        startupFlow = new StartupFlow(startupState);
        wakeMonitor=WakeMonitor.get(this);wakeState=wakeMonitor.state;wakeFlow=new WakeFlow(wakeState);wakeMonitor.listen(powerListener);
        startupPreferences.registerOnSharedPreferenceChangeListener(bootListener);
        startupFlow.restoreInteraction(!isHomeIntent(getIntent()));
        favorites = loadFavorites();
        shortcuts = new ShortcutStore(preferences);
        organizer = new AppOrganizer(getSharedPreferences("app_organizer", MODE_PRIVATE));
        updates=new UpdateRepository(this,new UpdateRepository.Listener(){@Override public void onUpdatesChanged(){applyUpdatesToUi();}});
        selected = clamp(preferences.getInt("selected_category", 0), 0, 4);
        if (state != null) {
            selected = clamp(state.getInt("selected", selected),0,4);
            screen = state.getInt("screen", HOME);
            page = state.getInt("page", 0);
            pickingSlot = state.getInt("picking", -1);
            pickingForStartup = state.getBoolean("picking_startup", false);
            // A system recreation is not a user cancellation; start a fresh visible countdown.
            startupFlow.restoreInteraction(!isHomeIntent(getIntent())
                    || state.getBoolean("startup_interacted", screen != HOME));
            if(pickingSlot<0||pickingSlot>=4)pickingSlot=-1;
            screen=clamp(screen,HOME,COLLECTION);
            pickingCollection=state.getBoolean("picking_collection",false)&&screen==APPS&&pickingSlot<0;
            collectionOriginPage=Math.max(0,state.getInt("collection_origin_page",0));
            collectionOriginFocus=state.getString("collection_origin_focus");
            pickerOriginFocus=state.getString("picker_origin_focus");
            updatesOrigin=state.getInt("updates_origin",APPS)==SETTINGS?SETTINGS:APPS;
            updatesOriginFocus=state.getString("updates_origin_focus");
            normalAppsPage=Math.max(0,state.getInt("normal_apps_page",0));
            settingsOrigin=state.getInt("settings_origin",HOME)==APPS?APPS:HOME;
            settingsOriginPage=Math.max(0,state.getInt("settings_origin_page",0));settingsOriginFocus=state.getString("settings_origin_focus");
            focusedKey = state.getString("focus");
            pendingBackupName=state.getString("pending_backup_name");
            wallpaperScene=state.getInt("wallpaper_scene",-1);
            pendingDiagnosticReport=state.getString("diagnostic_report");
            quickReturnScreen=state.getInt("quick_return_screen",-1);
            if(quickReturnScreen!=SETTINGS&&quickReturnScreen!=DEVICE)quickReturnScreen=-1;
            quickOrigin=clamp(state.getInt("quick_origin",HOME),HOME,COLLECTION);
            quickOriginPage=Math.max(0,state.getInt("quick_origin_page",0));quickOriginFocus=state.getString("quick_origin_focus");
            quickReturnFocus=state.getString("quick_return_focus");pendingQuickPage=state.getInt("quick_page",-1);
            pendingQuickFocus=state.getString("quick_focus");pendingQuickStandalone=state.getBoolean("quick_standalone",false);
        }
        if(childMode()){screen=HOME;page=0;pickingSlot=-1;pickingCollection=false;pickingForStartup=false;pendingQuickPage=-1;quickReturnScreen=-1;focusedKey="child_parent";}
        render(focusedKey);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;clearLaunchFeedback();noteInput();
        if(childMode()){screen=HOME;pickingSlot=-1;pickingCollection=false;pickingForStartup=false;pendingQuickPage=-1;quickReturnScreen=-1;uiNeedsRefresh=true;}
        diagnostics.event("返回桌面","页面 "+screen+" · 启动标识 "+BootSession.current(this));
        handler.removeCallbacks(diagnosticTick);handler.postDelayed(diagnosticTick,10000);
        if(!packageReceiverRegistered) {
            IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");
            // Only protected system broadcasts: the Android 14 receiver-flags exception applies.
            try{registerReceiver(packageReceiver,filter);packageReceiverRegistered=true;}catch(RuntimeException ignored){/* Resume/manual refresh still work. */}
        }
        immersive();
        deviceInfo=DeviceInfo.read(this);
        boolean changed=updatePerformance();
        if(scene!=null)scene.resume();
        if((changed||uiNeedsRefresh||screen==DEVICE)&&currentDialog==null)render(focusedKey);
        else if(changed||uiNeedsRefresh)refreshAfterDialog=true;
        uiNeedsRefresh=false;
        handler.removeCallbacks(ticker);
        ticker.run();
        refreshApps();
        updates.check(false);
        wakeMonitor.visibleHome(screen==HOME);
        wakeMonitor.refresh();
        checkStartup();
        if(configRecoveryError!=null)showConfigRecovery();
    }

    @Override protected void onPause() {
        diagnostics.event("离开桌面","页面 "+screen);handler.removeCallbacks(diagnosticTick);
        endParentSession();
        resumed = false;exitScreensaver();clearLaunchFeedback();handler.removeCallbacks(idleCheck);wakeKey=0;wakeTouch=false;
        wakeMonitor.refresh();
        if(currentDialog instanceof PhoneWallpaperDialog)dismissDialog();
        stopPackageReceiver();
        cancelConfirm();heldPageDirection=0;
        pauseStartup();
        handler.removeCallbacks(ticker);
        preferences.edit().putInt("selected_category", selected).apply();
        if(scene!=null)scene.pause();
        for(View view:controls.values()) {
            if(view instanceof DockTile)((DockTile)view).finishAnimation();
            else if(view instanceof AppTileView)((AppTileView)view).finishAnimation();
            else if(view instanceof SettingsTile)((SettingsTile)view).finishAnimation();
            else if(view instanceof SimpleHomeTile)((SimpleHomeTile)view).finishAnimation();
        }
        super.onPause();
    }

    @Override protected void onStop() {
        if(scene!=null)scene.releaseHidden();
        releaseIcons();
        diagnostics.event("后台释放","壁纸缓存 "+(scene==null?0:scene.cachedSceneCount())+" · 图标缓存 "+icons.size());
        super.onStop();
    }

    private void releaseIcons() {
        icons.clear();
        for(View view:controls.values())if(view instanceof AppTileView)((AppTileView)view).releaseIcon();
        if(isAppGrid())uiNeedsRefresh=true;
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(diagnostics!=null)diagnostics.event("内存回收通知","等级 "+level+" · "+(resumed?"前台":"后台"));
        if(level>=20&&!resumed) {
            if(scene!=null)scene.releaseHidden();
            releaseIcons();
        } else if(level>=5) {
            if(scene!=null)scene.trimMemory();
            icons.clear();
        }
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if(diagnostics!=null)diagnostics.event("内存紧张","系统要求释放资源");
        if(scene!=null){if(resumed)scene.trimMemory();else scene.releaseHidden();}
        icons.clear();
    }

    @Override protected void onDestroy() {
        diagnostics.event("桌面销毁","释放界面与工作队列");
        endParentSession();parentWorker.shutdownNow();
        destroyed = true;wallpaperCancelled=true;wallpaperWorker.shutdownNow();exitScreensaver();
        stopPackageReceiver();
        startupPreferences.unregisterOnSharedPreferenceChangeListener(bootListener);
        if(wakeMonitor!=null)wakeMonitor.detach(powerListener);
        dismissDialog();
        if(scene!=null)scene.close();
        worker.shutdownNow();
        if(updates!=null)updates.close();
        cancelConfirm();
        handler.removeCallbacksAndMessages(null);
        icons.clear();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("pending_backup_name",pendingBackupName);out.putInt("wallpaper_scene",wallpaperScene);
        out.putInt("selected",selected); out.putInt("screen", screen); out.putInt("page", page);
        out.putInt("picking", pickingSlot); out.putString("focus", focusedKey);
        out.putBoolean("picking_startup", pickingForStartup);
        out.putString("diagnostic_report",pendingDiagnosticReport);
        out.putBoolean("startup_interacted", startupFlow.interacted());
        out.putInt("updates_origin",updatesOrigin);out.putString("updates_origin_focus",updatesOriginFocus);out.putInt("normal_apps_page",normalAppsPage);
        out.putInt("settings_origin",settingsOrigin);out.putInt("settings_origin_page",settingsOriginPage);out.putString("settings_origin_focus",settingsOriginFocus);
        out.putBoolean("picking_collection",pickingCollection);
        out.putInt("collection_origin_page",collectionOriginPage);out.putString("collection_origin_focus",collectionOriginFocus);
        out.putString("picker_origin_focus",pickerOriginFocus);
        out.putInt("quick_return_screen",quickReturnScreen);out.putInt("quick_origin",quickOrigin);out.putInt("quick_origin_page",quickOriginPage);
        out.putString("quick_origin_focus",quickOriginFocus);out.putString("quick_return_focus",quickReturnFocus);
        if(currentDialog instanceof QuickSettingsDialog) {
            QuickSettingsDialog quick=(QuickSettingsDialog)currentDialog;
            out.putInt("quick_page",quick.page());out.putString("quick_focus",quick.focusedKey());out.putBoolean("quick_standalone",quick.standalone());
        }
        super.onSaveInstanceState(out);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        endParentSession();
        pauseStartup();
        if (isHomeIntent(intent)) go(HOME);
        checkStartup();
    }

    @Override public void startActivityForResult(Intent intent,int requestCode,Bundle options){
        if(childMode()){
            ComponentName component=intent==null?null:intent.getComponent();
            if(component==null||!Intent.ACTION_MAIN.equals(intent.getAction())||!parental.mayLaunch(component.getPackageName(),System.currentTimeMillis()))throw new SecurityException("未成年模式限制此入口");
        }
        super.startActivityForResult(intent,requestCode,options);
        if(wakeMonitor!=null)wakeMonitor.externalLaunch();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        endParentSession();
        if(configApplying){uiNeedsRefresh=true;return;}
        if(currentDialog instanceof QuickSettingsDialog) {
            QuickSettingsDialog quick=(QuickSettingsDialog)currentDialog;
            pendingQuickPage=quick.page();pendingQuickFocus=quick.focusedKey();pendingQuickStandalone=quick.standalone();
        }
        dismissDialog();
        icons.clear();
        render(focusedKey);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) { immersive(); checkStartup(); noteInput(); }
        else {exitScreensaver();handler.removeCallbacks(idleCheck);cancelConfirm();heldPageDirection=0;pauseStartup();}
    }

    private void immersive() {
        // Numeric constants avoid linking newer View fields on pre-KitKat devices.
        getWindow().getDecorView().setSystemUiVisibility(Build.VERSION.SDK_INT >= 19 ? 5894 : 1);
    }

    private int u(float value) { return Math.round(value * unit); }

    private boolean updatePerformance() {
        DisplayMetrics metrics=new DisplayMetrics();getWindowManager().getDefaultDisplay().getMetrics(metrics);
        PerformancePolicy next=PerformancePolicy.choose(preferences.getInt("performance_mode",0),
                deviceInfo.totalRam,deviceInfo.heapMb,deviceInfo.lowRam,Math.max(metrics.widthPixels,metrics.heightPixels));
        boolean changed=!next.sameLimits(performance);performance=next;
        if(changed){icons.clear();if(scene!=null)scene.setPolicy(next);}
        return changed;
    }

    private void render(String preferredKey) {
        if (destroyed) return;
        if(wakeMonitor!=null&&resumed)wakeMonitor.visibleHome(screen==HOME);
        if(configApplying){uiNeedsRefresh=true;return;}
        exitScreensaver();removeLaunchFeedbackView();cancelConfirm();focusReady=false;
        if(childMode()){screen=HOME;pickingSlot=-1;pickingCollection=false;pickingForStartup=false;pendingQuickPage=-1;}
        if(screen==HOME&&!childMode()&&!simpleHome())selected=workspace.visibleSelection(selected);
        noteInput();
        final String desired=preferredKey;
        DisplayMetrics metrics=new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        unit=Math.min(metrics.widthPixels/960f,metrics.heightPixels/540f);
        fontMode=DisplayPreferences.font(preferences);largeText=fontMode>0;
        motion=!preferences.getBoolean("reduce_motion",false);
        updatePerformance();
        controls.clear();focusRows.clear();nextId=1;
        clockText=null;dateText=null;networkText=null;networkIcon=null;gridDetail=null;
        heroTitle=null;heroSubtitle=null;startupHint=null;
        for(int i=0;i<dockTiles.length;i++)dockTiles[i]=null;
        if(surface==null) {
            surface=new FrameLayout(this);
            scene=new SceneView(this,performance);
            setContentView(surface);
        }
        surface.removeAllViews();
        surface.addView(scene,new FrameLayout.LayoutParams(-1,-1));
        scene.setGlass(null);scene.setMotion(motion);scene.setLibraryMode(isAppGrid());
        scene.setSettingsMode(screen==SETTINGS||screen==STARTUP||screen==DEVICE);
        scene.showScene(screen==SETTINGS||screen==STARTUP||screen==DEVICE?SceneView.SETTINGS_SCENE:isAppGrid()?4:selected,false);
        if(screen==HOME) {
            if(childMode())buildChildHome();else if(simpleHome())buildSimpleHome();else buildHome();
        } else if(isAppGrid()) {
            buildApps();
        } else if(screen==SETTINGS) {
            buildSettings();
        } else if(screen==STARTUP) {
            buildStartupSettings();
        } else {
            buildDeviceInfo();
        }
        updateClock();
        final View rendered=activeLayer;
        rendered.post(new Runnable(){@Override public void run(){
            if(destroyed||activeLayer!=rendered)return;
            wireFocus();
            if(screen==HOME&&!childMode()&&!simpleHome()) {
                for(int i=0;i<5;i++) {
                    View tile=controls.get("dock"+i);
                    if(tile==null)continue;
                    tile.setNextFocusUpId(controls.get("home_settings").getId());
                    tile.setNextFocusDownId(tile.getId());
                }
                controls.get("home_settings").setNextFocusDownId(controls.get("dock"+selected).getId());
                controls.get("home_network").setNextFocusDownId(controls.get("dock"+selected).getId());
            }
            if(screen==HOME&&!childMode()&&simpleHome()) {
                for(int i=0;i<3;i++){View tile=controls.get("simple"+i);tile.setNextFocusUpId(controls.get("home_settings").getId());tile.setNextFocusDownId(controls.get("simple_settings").getId());}
                controls.get("home_settings").setNextFocusDownId(controls.get(homeFocus()).getId());
                controls.get("home_network").setNextFocusDownId(controls.get(homeFocus()).getId());
                controls.get("simple_settings").setNextFocusUpId(controls.get(homeFocus()).getId());
            }
            View target=controls.get(desired);
            if(target==null)target=controls.get(screen==HOME?homeFocus():isAppGrid()?"app"+(page*PAGE_SIZE):screen==STARTUP?"startup0":screen==DEVICE?"device0":"setting0");
            if(target==null)target=controls.get(screen==COLLECTION?"collection_add":screen==RECENT?"recent_toggle":isAppGrid()?"refresh":"back");
            focusReady=true;
            if(target!=null)target.requestFocus();
            if(configRecoveryError!=null){showConfigRecovery();return;}
            if(pendingQuickPage>=0) {
                int restorePage=pendingQuickPage;pendingQuickPage=-1;
                showQuickSettings(restorePage,pendingQuickFocus,pendingQuickStandalone);
            }
        }});
    }

    private boolean simpleHome(){return !childMode()&&preferences.getBoolean("simple_home",false);}
    private boolean layoutLocked(){return preferences.getBoolean("layout_locked",false);}
    private boolean layoutEditable(){if(childMode()){openParentalSettings();return false;}if(configStore.pending()){showConfigRecovery();return false;}if(layoutLocked()){toast("布局已锁定，请到「家庭与配置」解锁后编辑");return false;}return true;}
    private String homeFocus(){if(childMode())return gridApps.isEmpty()?"child_parent":"app"+(page*PAGE_SIZE);return simpleHome()?"simple"+(selected==3?1:selected==4?2:0):"dock"+selected;}
    private void buildSimpleHome() {
        StageLayout home=new StageLayout(this);surface.addView(home,new FrameLayout.LayoutParams(-1,-1));activeLayer=home;
        int margin=DisplayPreferences.margin(preferences);float edge=new float[]{30,48,66}[margin],top=margin==2?36:24;
        home.place(text("栖屏",16,TEXT,false),edge+12,top+3,110,28);
        clockText=text("00:00",16,TEXT,false);clockText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);home.place(clockText,960-edge-158,top+3,60,28);
        networkIcon=new IconButton(this,scene,"network","网络设置");homeControl(networkIcon,"home_network",new Runnable(){public void run(){openNetworkSettings();}});home.place(networkIcon,960-edge-85,top-1,36,36);
        IconButton settings=new IconButton(this,scene,"settings","桌面设置");homeControl(settings,"home_settings",new Runnable(){public void run(){showQuickSettings(QuickSettingsDialog.MAIN,null,false);}});home.place(settings,960-edge-38,top-1,36,36);
        List<View> header=new ArrayList<View>();header.add(networkIcon);header.add(settings);focusRows.add(header);
        heroTitle=text("长辈布局",largeText?44:40,TEXT,true);home.place(heroTitle,edge+12,105,700,59);
        heroSubtitle=text("选择想要使用的功能",largeText?17:15,GlassPanel.SECONDARY,false);home.place(heroSubtitle,edge+14,164,760,27);
        startupHint=text("按返回取消本次自动打开",largeText?16:14,GlassPanel.SECONDARY,false);startupHint.setVisibility(View.GONE);home.place(startupHint,edge+14,193,700,26);
        String[] labels={"看电视","看照片","常用应用"},glyphs={"desktop","album","grid"};final int[] slots={1,3,4};
        float width=(960-2*edge-24)/3;List<View> row=new ArrayList<View>();
        for(int i=0;i<3;i++) {
            final int index=i;final String key="simple"+i;
            SimpleHomeTile tile=new SimpleHomeTile(this,scene,glyphs[i],labels[i],unit,largeText,motion);
            homeControl(tile,key,new Runnable(){public void run(){selectCategory(slots[index],true);openCategory(slots[index]);}});
            tile.setOnFocusChangeListener(new View.OnFocusChangeListener(){public void onFocusChange(View v,boolean focus){if(focus){rememberFocus(key);selectCategory(slots[index],true);}}});
            home.place(tile,edge+i*(width+12),223,width,220);row.add(tile);
        }focusRows.add(row);
        home.place(text(layoutLocked()?"布局已锁定 · 设置中可解锁":"上下左右选择 · 确定打开",largeText?16:14,GlassPanel.SECONDARY,false),edge+12,464,540,30);
        GlassButton family=new GlassButton(this,scene,null,text("布局与解锁",largeText?18:16,TEXT,false));family.setContentDescription("布局与解锁");homeControl(family,"simple_settings",new Runnable(){public void run(){showQuickSettings(QuickSettingsDialog.FAMILY,null,true);}});home.place(family,960-edge-176,458,176,44);
        List<View> footer=new ArrayList<View>();footer.add(family);focusRows.add(footer);updateHero();
    }

    private void buildHome() {
        StageLayout home=new StageLayout(this);
        surface.addView(home,new FrameLayout.LayoutParams(-1,-1));activeLayer=home;
        int margin=clamp(preferences.getInt("margin",1),0,2);
        float edge=new float[]{30,48,66}[margin];
        float top=margin==2?36:24;
        home.place(text("栖屏",16,TEXT,false),edge+12,top+3,110,28);
        clockText=text("00:00",16,TEXT,false);clockText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        home.place(clockText,960-edge-158,top+3,60,28);
        networkIcon=new IconButton(this,scene,"network","网络设置");
        homeControl(networkIcon,"home_network",new Runnable(){@Override public void run(){openNetworkSettings();}});
        home.place(networkIcon,960-edge-85,top-1,36,36);
        final IconButton settings=new IconButton(this,scene,"settings","桌面设置");
        homeControl(settings,"home_settings",new Runnable(){@Override public void run(){settings.requestFocus();showQuickSettings(QuickSettingsDialog.MAIN,null,false);}});
        home.place(settings,960-edge-38,top-1,36,36);
        List<View> topFocus=new ArrayList<View>();topFocus.add(networkIcon);topFocus.add(settings);focusRows.add(topFocus);
        heroTitle=text(ShortcutStore.NAMES[selected],largeText?50:44,TEXT,true);
        heroTitle.setShadowLayer(u(4),0,u(2),0x55000000);
        heroSubtitle=text(DESCRIPTIONS[selected],largeText?20:18,0xFFD1D4DA,false);
        heroSubtitle.setSingleLine();heroSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        heroSubtitle.setShadowLayer(u(3),0,u(1),0x66000000);
        home.place(heroTitle,edge+22,163,450,66);
        home.place(heroSubtitle,edge+24,231,430-(edge-48),34);
        startupHint=text("按返回取消本次自动打开",largeText?16:14,GlassPanel.SECONDARY,false);
        startupHint.setVisibility(View.GONE);
        home.place(startupHint,edge+24,273,430,28);
        float dockHeight=DockTile.CANVAS_HEIGHT, dockBottom=margin==2?483:494;
        float dockY=dockBottom-dockHeight, dockWidth=960-2*edge;
        GlassDock glass=new GlassDock(this,scene);home.place(glass,edge,dockY,dockWidth,dockHeight);scene.addGlass(glass);
        String[] glyphs={"film","live","music","album","grid"};
        List<View> dockFocus=new ArrayList<View>();int[] visible=workspace.home(true);float slotWidth=(dockWidth-20)/visible.length;
        for(int position=0;position<visible.length;position++) {
            int i=visible[position];final int index=i;
            DockTile tile=new DockTile(this,scene,glyphs[i]);
            tile.setMotion(motion);tile.setChosen(i==selected);tile.setUpdateAvailable(slotHasUpdate(i));
            tile.setContentDescription(ShortcutStore.NAMES[i]+(slotHasUpdate(i)?"，有更新":""));
            tile.setId(nextId++);final Tag tag=new Tag("dock"+i,null,i);tile.setTag(tag);controls.put(tag.key,tile);
            tile.setOnFocusChangeListener(new View.OnFocusChangeListener(){@Override public void onFocusChange(View v,boolean focused){
                if(focused){rememberFocus(tag.key);selectCategory(index,true);}
            }});
            tile.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){selectCategory(index,true);openCategory(index);}});
            if(i<4)tile.setOnLongClickListener(new View.OnLongClickListener(){@Override public boolean onLongClick(View v){slotMenu(index);return true;}});
            dockTiles[i]=tile;dockFocus.add(tile);home.place(tile,edge+10+position*slotWidth,dockY,slotWidth,dockHeight);
        }
        focusRows.add(dockFocus);
        IconButton more=new IconButton(this,scene,"down","打开全部应用");more.setFocusable(false);more.setFocusableInTouchMode(false);
        more.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){go(APPS);}});
        home.place(more,464,dockBottom+2,32,30);
        updateHero();
    }

    private void homeControl(View view,final String key,final Runnable action) {
        view.setId(nextId++);view.setTag(new Tag(key,null,-1));controls.put(key,view);
        view.setOnFocusChangeListener(new View.OnFocusChangeListener(){@Override public void onFocusChange(View v,boolean focused){if(focused)rememberFocus(key);}});
        view.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){action.run();}});
    }
    private void selectCategory(int index,boolean animate) {
        if(index<0||index>4)return;
        boolean changed=selected!=index;selected=index;
        for(int i=0;i<5;i++)if(dockTiles[i]!=null)dockTiles[i].setChosen(i==selected);
        updateHero();scene.showScene(selected,animate&&changed);
        View selectedView=controls.get(homeFocus());
        if(selectedView!=null) {
            View settings=controls.get("home_settings"),network=controls.get("home_network");
            if(settings!=null)settings.setNextFocusDownId(selectedView.getId());
            if(network!=null)network.setNextFocusDownId(selectedView.getId());
        }
    }
    private void updateHero() {
        if(childMode()){updateChildHome();return;}
        if(heroTitle==null)return;
        heroTitle.setText(simpleHome()?"长辈布局":ShortcutStore.NAMES[selected]);
        if(simpleHome())for(int i=0;i<3;i++) {
            View v=controls.get("simple"+i);if(!(v instanceof SimpleHomeTile))continue;
            int slot=i==0?1:i==1?3:4;AppEntry app=slot<4?byPackage(shortcuts.get(slot)):null;
            String hint=slot==4?"全部应用与收藏":app!=null?app.label:loading?"正在读取应用…":shortcuts.get(slot).length()>0?"应用未安装或不可用":layoutLocked()?"尚未绑定 · 请先解锁":"尚未绑定 · 确定选择";
            ((SimpleHomeTile)v).setHint(hint);((SimpleHomeTile)v).setUpdateAvailable(slotHasUpdate(slot));
        }
        AppEntry bound=selected<4?byPackage(shortcuts.get(selected)):null;
        if(startupTarget!=null) {
            long seconds=Math.max(1,(startupDeadline-SystemClock.elapsedRealtime()+999)/1000);
            heroSubtitle.setText((wakeCountdown?"唤醒后 ":"")+seconds+" 秒后打开 "+startupTarget.label);
        } else heroSubtitle.setText(bound==null?DESCRIPTIONS[selected]:"打开 "+bound.label);
        if(startupHint!=null)startupHint.setVisibility(startupTarget==null?View.GONE:View.VISIBLE);
    }
    private void openCategory(int index) {
        if(index==4){go(APPS);return;}
        AppEntry entry=byPackage(shortcuts.get(index));
        if(entry!=null)openApp(entry);
        else if(loading)toast("正在读取应用，请稍候再按确定");
        else beginPick(index);
    }

    private void buildApps() {
        final boolean updateList=screen==UPDATES;
        gridApps.clear();gridApps.addAll(visibleGrid());
        StageLayout layout=new StageLayout(this);
        layout.setPageSwipe(new StageLayout.PageSwipe(){@Override public void onPageSwipe(boolean right){swipePage(right);}});
        surface.addView(layout,new FrameLayout.LayoutParams(-1,-1));activeLayer=layout;
        int margin=clamp(preferences.getInt("margin",1),0,2);
        float edge=new float[]{30,48,66}[margin],top=margin==2?36:24;
        int pages=Math.max(1,(gridApps.size()+PAGE_SIZE-1)/PAGE_SIZE);page=clamp(page,0,pages-1);
        GlassButton back=glassButton("back","back",(updateList||isOrganizedGrid()||pickingCollection)?"返回上一界面":pickingForStartup?"返回开机设置":"返回首页",new Runnable(){@Override public void run(){onBackPressed();}});
        layout.place(back,edge,top-4,44,44);layout.place(text("栖屏",16,TEXT,false),edge+58,top+4,120,28);
        clockText=text("00:00",16,TEXT,false);clockText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        layout.place(clockText,960-edge-184,top+4,64,28);
        List<View> headerFocus=new ArrayList<View>();headerFocus.add(back);
        if(screen==APPS&&pickingSlot<0&&!pickingCollection) {
            GlassButton recent=toolbarText("apps_recent","最近打开",new Runnable(){@Override public void run(){openOrganized(RECENT);}});
            layout.place(recent,960-edge-414,top-4,104,44);headerFocus.add(recent);
            GlassButton saved=toolbarText("apps_collection","收藏",new Runnable(){@Override public void run(){openOrganized(COLLECTION);}});
            layout.place(saved,960-edge-298,top-4,94,44);headerFocus.add(saved);
            GlassButton updateButton=new GlassButton(this,scene,null,text("更新",14,TEXT,false));
            homeControl(updateButton,"apps_updates",new Runnable(){@Override public void run(){openUpdates();}});
            updateButton.setContentDescription("应用更新");layout.place(updateButton,960-edge-104,top-4,50,44);headerFocus.add(updateButton);
        }
        if(screen==RECENT) {
            GlassButton toggle=toolbarText("recent_toggle",organizer.enabled()?"记录：开":"记录：关",new Runnable(){@Override public void run(){toggleRecent();}});
            layout.place(toggle,960-edge-422,top-4,116,44);headerFocus.add(toggle);
            GlassButton clear=toolbarText("recent_clear","清空记录",new Runnable(){@Override public void run(){clearRecent();}});
            layout.place(clear,960-edge-294,top-4,104,44);headerFocus.add(clear);
        } else if(screen==COLLECTION) {
            GlassButton add=toolbarText("collection_add","添加应用",new Runnable(){@Override public void run(){beginCollectionPick();}});
            layout.place(add,960-edge-422,top-4,116,44);headerFocus.add(add);
            GlassButton manage=toolbarText("collection_manage","整理",new Runnable(){@Override public void run(){organizeCollection();}});
            layout.place(manage,960-edge-294,top-4,104,44);headerFocus.add(manage);
        }
        GlassButton refresh=glassButton("refresh","refresh",updateList?"检查应用更新":"刷新应用",new Runnable(){@Override public void run(){
            if(isOrganizedGrid())organizer.reload();
            refreshApps();updates.check(true);
            if(updateList&&!updates.configured())toast("尚未接入更新服务，可在应用管理菜单中前往应用商店");
        }});
        layout.place(refresh,960-edge-44,top-4,44,44);headerFocus.add(refresh);focusRows.add(headerFocus);
        TextView title=text(screen==RECENT?"最近打开":screen==COLLECTION?"我的收藏":pickingCollection?"添加收藏":updateList?"应用更新":pickingSlot>=0?"选择"+ShortcutStore.NAMES[pickingSlot]+"应用":"全部应用",largeText?40:36,TEXT,true);
        title.setShadowLayer(u(4),0,u(2),0x66000000);layout.place(title,edge+10,top+51,700,55);
        gridDetail=text(appsDetail(),largeText?16:14,GlassPanel.SECONDARY,false);gridDetail.setSingleLine();gridDetail.setEllipsize(TextUtils.TruncateAt.END);
        layout.place(gridDetail,edge+12,top+107,960-2*edge-24,23);
        float slotWidth=(960-2*edge)/6,gridY=top+125;
        if(gridApps.isEmpty()) {
            GlassPanel emptyPanel=new GlassPanel(this,scene);layout.place(emptyPanel,edge+80,gridY+22,960-2*edge-160,228);
            TextView empty=text(emptyGridTitle(),largeText?24:22,TEXT,false);
            empty.setGravity(Gravity.CENTER);layout.place(empty,edge+96,gridY+70,960-2*edge-192,52);
            TextView note=text(emptyGridMessage(),largeText?17:15,GlassPanel.SECONDARY,false);
            note.setGravity(Gravity.CENTER);layout.place(note,edge+110,gridY+132,960-2*edge-220,66);
        } else {
            for(int r=0;r<2;r++) {
                List<View> focus=new ArrayList<View>();
                for(int c=0;c<6;c++) {
                    final int index=page*PAGE_SIZE+r*6+c;
                    if(index<gridApps.size()) {
                        final AppEntry entry=gridApps.get(index);
                        AppTileView tile=libraryTile("app"+index,entry,new Runnable(){@Override public void run(){
                            if(pickingSlot>=0) {
                                int slot=pickingSlot;shortcuts.put(slot,entry.packageName);pickingSlot=-1;
                                if(pickingForStartup){pickingForStartup=false;screen=STARTUP;render("startup2");}
                                else{screen=HOME;selected=slot;render("dock"+slot);}
                                toast("已绑定到"+ShortcutStore.NAMES[slot]);
                            }else if(pickingCollection) {
                                int result=organizer.add(entry.packageName);
                                if(result==AppOrganizer.OK||result==AppOrganizer.EXISTS) {
                                    pickingCollection=false;screen=COLLECTION;page=0;
                                    render(gridFocusForPackage(entry.packageName));
                                    toast(result==AppOrganizer.OK?"已加入收藏":"已在收藏中");
                                }else organizerResult(result);
                            }else openApp(entry);
                        }});
                        focus.add(tile);layout.place(tile,edge+c*slotWidth,gridY+r*144,slotWidth,138);
                    }
                }
                if(!focus.isEmpty())focusRows.add(focus);
            }
        }
        // Overflow cues have no focus, click handlers or page numbers.
        if(page>0)layout.place(new PageEdgeView(this,scene,false),edge-22,gridY+108,16,42);
        if(page<pages-1)layout.place(new PageEdgeView(this,scene,true),960-edge+6,gridY+108,16,42);
        float footerY=margin==2?466:474;
        String hint=pickingCollection?"方向键选择 · 左右到边缘翻页 · 确定收藏 · 返回取消":screen==COLLECTION?"确定打开 · 长按或菜单键调整收藏 · 返回全部应用":screen==RECENT?"仅记录从栖屏发起的打开请求 · 不含观看历史":pickingSlot>=0?"方向键选择 · 左右到边缘翻页 · 确定绑定 · 返回取消":"方向键选择 · 左右到边缘翻页 · 确定打开 · 长按管理";
        boolean canManage=screen==APPS&&pickingSlot<0&&!pickingCollection;
        layout.place(text(hint,largeText?14:12,GlassPanel.SECONDARY,false),edge+12,footerY,960-2*edge-(canManage?174:24),32);
        if(canManage){GlassButton manage=toolbarText("apps_manage","整理应用",new Runnable(){public void run(){showAppManager();}});layout.place(manage,960-edge-142,footerY-4,142,40);List<View> footer=new ArrayList<View>();footer.add(manage);focusRows.add(footer);}
    }

    private GlassButton toolbarText(String key,String label,final Runnable action) {
        TextView caption=text(label,largeText?18:16,TEXT,false);
        caption.setSingleLine();caption.setEllipsize(TextUtils.TruncateAt.END);
        final GlassButton button=new GlassButton(this,scene,null,caption);
        button.setContentDescription(label);homeControl(button,key,new Runnable(){@Override public void run(){button.requestFocus();action.run();}});return button;
    }

    private List<AppEntry> visibleGrid() {
        List<AppEntry> result=new ArrayList<AppEntry>();unavailableCount=0;
        if(childMode()){if(!parental.timeAllowed(System.currentTimeMillis())||scanError)return result;for(String pkg:parental.allowed()){AppEntry app=byPackage(pkg);if(app!=null)result.add(app);}return result;}
        if(isOrganizedGrid()) {
            // A failed scan is not proof of an uninstall: never prune persisted data here.
            if(scanError)return result;
            for(AppOrganizer.Item item:screen==RECENT?organizer.recent():organizer.saved()) {
                AppEntry installed=byPackage(item.packageName);
                if(installed!=null)result.add(installed);else unavailableCount++;
            }
        }else for(AppEntry app:apps)if(screen!=UPDATES||updates.available(app)!=null)result.add(app);
        return screen==APPS&&pickingSlot<0&&!pickingCollection?workspace.visible(result):result;
    }

    private String emptyGridTitle() {
        if(screen==UPDATES)return updates.status(0);
        if(screen==RECENT&&organizer.recentError())return "暂时无法读取最近记录";
        if(screen==COLLECTION&&organizer.savedError())return "暂时无法读取收藏";
        if(loading)return "正在整理应用";
        if(scanError)return "暂时无法读取应用";
        if(isOrganizedGrid()&&unavailableCount>0)return "列表中的应用暂不可用";
        if(screen==RECENT)return organizer.enabled()?"还没有最近打开记录":"最近记录已关闭";
        if(screen==COLLECTION)return "把常用的应用放在这里";
        if(screen==APPS&&!apps.isEmpty()&&pickingSlot<0&&!pickingCollection)return "应用已隐藏";
        return "还没有可打开的应用";
    }

    private String emptyGridMessage() {
        if(screen==UPDATES)return updates.emptyMessage();
        if(screen==RECENT&&organizer.recentError())return "可以刷新重试，或选择「清空记录」重新开始";
        if(screen==COLLECTION&&organizer.savedError())return "可以刷新重试，或在「整理」中确认重置收藏";
        if(loading)return "稍等片刻，应用将在这里显示";
        if(scanError)return "使用右上角刷新按钮重试，返回键可退出";
        if(isOrganizedGrid()&&unavailableCount>0)return screen==RECENT?"应用可能已卸载或停用；可以刷新或清空记录":"应用可能已卸载或停用；可在「整理」中移除不可用项";
        if(screen==RECENT)return organizer.enabled()?"从栖屏打开应用后会记录在这里，最多保留 12 个":"使用上方「记录：关」可重新开启；关闭期间不记录";
        if(screen==COLLECTION)return "选择上方「添加应用」，或在全部应用中长按加入收藏";
        if(screen==APPS&&!apps.isEmpty()&&pickingSlot<0&&!pickingCollection)return "选择右下角「整理应用」，可找回已隐藏的应用";
        return "安装应用后，使用右上角刷新按钮";
    }

    private void openOrganized(int destination) {
        collectionOriginPage=page;collectionOriginFocus=focusedKey;
        screen=destination;page=0;pickingCollection=false;
        render(rememberedFocus[destination]);
    }

    private void beginCollectionPick() {
        if(!layoutEditable())return;
        if(organizer.savedError()){organizeCollection();return;}
        if(organizer.size()>=AppOrganizer.LIMIT){toast("收藏已满 12 个，请先移出一个或整理不可用项");return;}
        pickerOriginFocus=focusedKey;pickingCollection=true;pickingSlot=-1;pickingForStartup=false;
        screen=APPS;page=0;render("app0");
    }

    private void toggleRecent() {
        if(organizerResult(organizer.setEnabled(!organizer.enabled())))render("recent_toggle");
    }

    private void clearRecent() {
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("清空最近记录？")
                .setMessage("只清除栖屏本机的打开记录，不影响收藏、首页绑定和应用自身的数据。记录开关保持当前状态。")
                .setPositiveButton("清空记录",new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface d,int which){
                    if(organizerResult(organizer.clearRecent())){render("recent_clear");toast("最近记录已清空");}
                }}).setNegativeButton("取消",null));
    }

    private boolean organizerResult(int result) {
        if(result==AppOrganizer.OK)return true;
        toast(result==AppOrganizer.EXISTS?"已在收藏中":result==AppOrganizer.FULL?"收藏已满 12 个，请先移出一个":
                result==AppOrganizer.READ_ERROR?"列表读取失败，请先刷新或重置":result==AppOrganizer.WRITE_ERROR?"保存失败，请重试":
                result==AppOrganizer.EDGE?"已经到达列表边缘":result==AppOrganizer.MISSING?"此应用已不在收藏中":"暂时无法保存此应用");
        return false;
    }

    private void toggleCollection(AppEntry entry) {
        if(!layoutEditable())return;
        boolean existed=organizer.contains(entry.packageName);
        if(organizerResult(existed?organizer.remove(entry.packageName):organizer.add(entry.packageName))) {
            render(gridFocusForPackage(entry.packageName));toast(existed?"已移出收藏，应用仍保留":"已加入收藏");
        }
    }

    private void collectionMenu(final AppEntry entry) {
        if(!layoutEditable())return;
        final List<String> names=new ArrayList<String>();final List<Integer> actions=new ArrayList<Integer>();
        int position=organizer.position(entry.packageName);
        if(position<0){render(gridFocusForPackage(entry.packageName));return;}
        if(position>0){names.add("向左移动");actions.add(-1);}
        if(position+1<organizer.size()){names.add("向右移动");actions.add(1);}
        names.add("移出收藏");actions.add(0);
        names.add("打开应用");actions.add(2);
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(entry.label).setSidePanel()
                .setMessage("调整收藏顺序；移出不会卸载应用")
                .setItems(names.toArray(new String[names.size()]),new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface d,int which){
                    int action=actions.get(which);
                    if(action==2){openApp(entry);return;}
                    if(organizerResult(action==0?organizer.remove(entry.packageName):organizer.move(entry.packageName,action))) {
                        render(gridFocusForPackage(entry.packageName));
                        if(action==0)toast("已移出收藏，应用仍保留");
                    }
                }}).setNegativeButton("取消",null));
        View target=controls.get(focusedKey);if(target instanceof AppTileView)((AppTileView)target).setMenuTarget(true);
    }

    private void organizeCollection() {
        if(!layoutEditable())return;
        if(organizer.savedError()) {
            dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("重置无法读取的收藏？")
                    .setMessage("这会清空本轮新增的独立收藏列表。首页绑定和最近记录保持不变。")
                    .setPositiveButton("重置收藏",new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface d,int which){
                        if(organizerResult(organizer.clearSaved()))render("collection_add");
                    }}).setNegativeButton("取消",null));return;
        }
        if(loading||scanError){toast("请先刷新，等应用列表读取完成后再整理");return;}
        final java.util.Set<String> installed=new java.util.HashSet<String>();for(AppEntry app:apps)installed.add(app.packageName);
        int missing=0;for(AppOrganizer.Item item:organizer.saved())if(!installed.contains(item.packageName))missing++;
        if(missing==0) {
            dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("整理收藏")
                    .setMessage("收藏中没有不可用的应用。\n\n选中应用后长按确定或按菜单键，可向左移动、向右移动或移出收藏。")
                    .setPositiveButton("知道了",null));return;
        }
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("移除 "+missing+" 个不可用项？")
                .setMessage("这些应用已卸载、停用或没有可打开的入口。只移除收藏记录，不卸载应用；也可以取消后刷新重试。")
                .setPositiveButton("移除不可用项",new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface d,int which){
                    // Recheck after confirmation: never clean using an incomplete or stale scan.
                    if(loading||scanError){toast("应用列表正在变化，请刷新后重试");return;}
                    java.util.Set<String> current=new java.util.HashSet<String>();for(AppEntry app:apps)current.add(app.packageName);
                    if(organizerResult(organizer.removeUnavailable(current)))render("collection_manage");
                }}).setNegativeButton("取消",null));
    }

    private String appsDetail() {
        if(screen==UPDATES)return updates.status(updateCount());
        if(isOrganizedGrid()) {
            boolean history=screen==RECENT;
            if(history?organizer.recentError():organizer.savedError())return history?"记录读取失败，可清空后重新记录":"收藏读取失败，可在「整理」中重置";
            if(loading)return "正在核对已安装的应用…";
            if(scanError)return "应用列表读取失败，请刷新重试";
            String state=history?(organizer.enabled()?"记录已开启":"记录已关闭，已有记录保留")+" · 最近 "+gridApps.size()+" 个":gridApps.size()+" 个可用收藏 · 最多 12 个";
            return state+(unavailableCount>0?" · "+unavailableCount+" 个应用已卸载或停用":"");
        }
        if(pickingCollection)return "选择应用加入独立收藏，不改变首页绑定 · 已收藏 "+organizer.size()+" / 12";
        return loading?"正在读取应用…":scanError?"刷新失败，请重试":pickingForStartup?"用于开机自动打开，同时设为首页「直播」入口":pickingSlot>=0?"选择后将设为首页「"+ShortcutStore.NAMES[pickingSlot]+"」入口":"显示 "+gridApps.size()+" / "+apps.size()+" 个应用"+(workspace.hiddenApps().isEmpty()?"":" · 可在整理中恢复隐藏")+(updateCount()>0?" · "+updateCount()+" 个应用有更新":"");
    }

    private GlassButton glassButton(String key,String glyph,String label,Runnable action) {
        GlassButton button=new GlassButton(this,scene,glyph,null);button.setContentDescription(label);
        homeControl(button,key,action);return button;
    }

    private AppTileView libraryTile(String key,AppEntry entry,final Runnable action) {
        boolean pinned=shortcuts.contains(entry.packageName);
        AppTileView tile=new AppTileView(this,scene,icon(entry),entry.label,pinned,largeText,motion,unit);
        tile.setId(nextId++);final Tag tag=new Tag(key,entry,-1);tile.setTag(tag);controls.put(key,tile);
        tile.setUpdateAvailable(updates.available(entry)!=null);
        tile.setContentDescription(entry.label+(pinned?"，已绑定首页入口":"")+(updates.available(entry)!=null?"，有更新":""));
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener(){@Override public void onFocusChange(View view,boolean focused){if(focused)rememberFocus(tag.key);}});
        tile.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View view){action.run();}});
        tile.setOnLongClickListener(new View.OnLongClickListener(){@Override public boolean onLongClick(View view){view.requestFocus();manage(tag);return true;}});
        return tile;
    }

    private void buildSettings() {
        StageLayout layout=new StageLayout(this);surface.addView(layout,new FrameLayout.LayoutParams(-1,-1));activeLayer=layout;
        int margin=clamp(preferences.getInt("margin",1),0,2);
        float edge=new float[]{30,48,66}[margin],top=margin==2?36:24;
        GlassButton back=glassButton("back","back","返回首页",new Runnable(){@Override public void run(){onBackPressed();}});
        layout.place(back,edge,top-4,44,44);layout.place(text("栖屏",16,TEXT,false),edge+58,top+4,120,28);
        clockText=text("00:00",16,TEXT,false);clockText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        layout.place(clockText,960-edge-90,top+4,80,28);
        List<View> header=new ArrayList<View>();header.add(back);focusRows.add(header);
        TextView title=text("桌面设置",largeText?40:36,TEXT,true);title.setShadowLayer(u(4),0,u(2),0x66000000);
        layout.place(title,edge+10,top+51,650,55);
        layout.place(text("外观与使用偏好",largeText?16:14,GlassPanel.SECONDARY,false),edge+12,top+107,640,23);
        String[] titles={"开机与桌面","家庭与配置","网络设置","系统设置","其他桌面","设备与性能","应用更新","个性设置","使用说明"};
        String[] subtitles={startupState.enabled()||wakeState.enabled()?"开机 / 唤醒直播已开启":"默认桌面与开机直播","长辈布局、未成年模式与备份","Wi-Fi 与有线网络","声音、显示与应用","打开原厂桌面",performance.light?"设备信息 · 轻量运行":"设备信息 · 标准运行","查看已接入的应用更新","外观、布局与显示偏好","遥控器操作与功能说明"};
        String[] glyphs={"home","lock","network","settings","desktop","info","refresh","motion","text"};
        float slotWidth=(960-2*edge)/3,gridY=top+132;
        for(int r=0;r<3;r++) {
            List<View> focus=new ArrayList<View>();
            for(int c=0;c<3;c++) {
                final int action=r*3+c;
                int toggle=-1;
                SettingsTile tile=new SettingsTile(this,scene,glyphs[action],titles[action],subtitles[action],toggle,largeText,motion,unit);
                homeControl(tile,"setting"+action,new Runnable(){@Override public void run(){setting(action);}});
                layout.place(tile,edge+c*slotWidth,gridY+r*100,slotWidth,100);focus.add(tile);
            }
            focusRows.add(focus);
        }
        float footerY=margin==2?478:482;
        layout.place(text("方向键选择  ·  确定调整",largeText?14:12,GlassPanel.SECONDARY,false),edge+12,footerY,400,28);

    }

    private void showQuickSettings(int initialPage,String initialKey,boolean standalone) {
        if(childMode()){openParentalSettings();return;}
        showDialog(new QuickSettingsDialog(this,scene,unit,initialPage,initialKey,standalone,new QuickSettingsDialog.Actions(){
            @Override public void displayChanged(){render(focusedKey);}
            @Override public void openDevice(){openQuickDestination(DEVICE,"quick_device");}
            @Override public void openSystem(){openSettings();}
            @Override public void openAllSettings(){openQuickDestination(SETTINGS,"quick_all");}
            @Override public void personalAction(int action){
                if(action==0)showHomeLayout();else if(action==1)showAppManager();else if(action==2)showScreensaverSettings();else showWallpapers();
            }
            @Override public void openParental(){openParentalSettings();}
            @Override public void backupAction(int action){configAction(action);}
        }));
    }

    private void openQuickDestination(int destination,String returnFocus) {
        quickOrigin=screen;quickOriginPage=page;quickOriginFocus=focusedKey;
        quickReturnScreen=destination;quickReturnFocus=returnFocus;
        dismissDialog();screen=destination;page=0;
        if(destination==DEVICE)deviceInfo=DeviceInfo.read(this);
        render(destination==DEVICE?"device0":"setting0");
    }

    private void returnToQuickSettings() {
        screen=quickOrigin;page=quickOriginPage;quickReturnScreen=-1;
        pendingQuickPage=QuickSettingsDialog.MAIN;pendingQuickFocus=quickReturnFocus;pendingQuickStandalone=false;
        render(quickOriginFocus);
    }

    private void setting(int action) {
        if(childMode()){openParentalSettings();return;}
        if(action==0)go(STARTUP);
        else if(action==1)showQuickSettings(QuickSettingsDialog.FAMILY,null,true);
        else if(action==2)openNetworkSettings();
        else if(action==3)openSettings();
        else if(action==4)chooseOtherHome();
        else if(action==5){deviceInfo=DeviceInfo.read(this);go(DEVICE);}
        else if(action==6)openUpdates();
        else if(action==7)showPersonalization();
        else showUsage();
    }

    private void showUsage() {
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("栖屏桌面 "+launcherVersion)
                .setMessage("左右切换图标，确定打开；首次使用时选择对应应用。上键进入右上角设置，下键或最右侧继续按右打开全部应用。全部应用在左右边缘直接翻页，手机可左右滑动。\n\n菜单键或长按确定可管理应用。右上角齿轮打开快捷设置，「全部设置」中的「个性设置」统一调整字号、边距、动效、布局、屏保与壁纸。界面动效同时控制背景切换与焦点过渡。「全部设置」大卡片中的「家庭与配置」可开启长辈布局、锁定绑定与收藏编辑，、设置未成年模式，或备份和恢复配置；长辈首页底部的「布局与解锁」可关闭长辈布局和解锁。恢复前先预览，确认后才应用，支持撤销；开机开关和本次启动记录保持本机状态。设置、应用选择和弹窗均支持方向键；长说明可上下滚动。返回键回到上一级并恢复焦点。更新标记仅来自已接入的更新服务，按确定可前往应用商店更新。\n\n背景为内置主题图片，不代表应用实时内容。影视、直播、音乐和相册由你绑定的已安装应用提供。\n\n设备与性能页读取当前运行设备的数据。自动模式会为低内存设备选择轻量配置，也可以手动选择轻量模式。\n\n适用系统 Android 9 及以上；默认桌面与开机行为以电视实际固件为准。")
                .setPositiveButton("知道了",null));
    }

    private void configPanel(final String row){showQuickSettings(QuickSettingsDialog.CONFIG,row,true);}
    private void configDialog(GlassDialog.Builder builder,final String row){showDialog(builder.create(),new Runnable(){public void run(){configPanel(row);}});}
    private interface ConfigWork {Object run()throws Exception;}
    private interface ConfigResult {void done(Object result);}
    private void configWork(String title,final String row,final boolean applying,final ConfigWork work,final ConfigResult result) {
        final int generation=++configGeneration;configApplying=applying;
        Dialog busy=new GlassDialog.Builder(this,scene,unit,largeText).setTitle(title).setMessage("正在处理，请稍候…").create();busy.setCancelable(false);busy.setCanceledOnTouchOutside(false);showDialog(busy);
        worker.execute(new Runnable(){public void run(){
            Object value=null;Exception failure=null;try{value=work.run();}catch(Exception error){failure=error;}
            final Object completed=value;final Exception error=failure;
            handler.post(new Runnable(){public void run(){
                if(destroyed||generation!=configGeneration)return;configApplying=false;
                if(error!=null) {
                    if(configStore.pending()){configRecoveryError="上次恢复未完成，请腾出存储空间后重试还原";showConfigRecovery();}
                    else configDialog(new GlassDialog.Builder(MainActivity.this,scene,unit,largeText).setTitle("操作未完成").setMessage(configError(error)).setPositiveButton("知道了",null),row);
                    return;
                }
                result.done(completed);
            }});
        }});
    }
    private String configError(Exception error){String message=error.getMessage();return message!=null&&message.matches("(?s).*[\\u4e00-\\u9fff].*")?message:"无法读写文件，请检查所选文件和存储空间后重试。";}
    private void configAction(int action) {
        if(childMode()){openParentalSettings();return;}
        if(configStore.pending()){showConfigRecovery();return;}
        if(action==0) {
            configWork("备份配置","config_export",false,new ConfigWork(){public Object run()throws Exception{return configStore.backup();}},new ConfigResult(){public void done(Object value){
                final File file=(File)value;pendingBackupName=file.getName();
                configDialog(new GlassDialog.Builder(MainActivity.this,scene,unit,largeText).setTitle("本机备份已保存")
                    .setMessage("应用内保留最近 5 份手动备份，可在恢复配置中选择。\n\n卸载或清除栖屏数据会删除这些副本。需要留存到电视之外时，请再导出文件。")
                    .setPositiveButton(Build.VERSION.SDK_INT>=19?"另存为文件":"导出到本机目录",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){exportConfig(file);}}).setNegativeButton("完成",null),"config_export");
            }});
        } else if(action==1) {
            if(!layoutEditable())return;
            configDialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("恢复配置").setSidePanel()
                .setMessage("选定后先预览，确认才替换设置。")
                .setItems(new String[]{"选择备份文件","应用内备份"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==0)pickConfigFile();else chooseLocalConfig();}}).setNegativeButton("取消",null),"config_import");
        } else {
            if(!layoutEditable())return;
            if(!configStore.canUndo()){configDialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("暂无可撤销的恢复").setMessage("成功恢复配置后，会保留恢复前的设置供撤销。手动备份仍在恢复配置中。" ).setPositiveButton("知道了",null),"config_undo");return;}
            configWork("读取恢复前配置","config_undo",false,new ConfigWork(){public Object run()throws Exception{return configStore.undoPreview();}},new ConfigResult(){public void done(Object value){previewConfig((ConfigBackup)value,true);}});
        }
    }
    private void exportConfig(final File source) {
        pendingBackupName=source.getName();
        if(Build.VERSION.SDK_INT>=19) {
            Intent intent=new Intent("android.intent.action.CREATE_DOCUMENT").addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE,source.getName());intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);
            dismissDialog();try{startActivityForResult(intent,CONFIG_EXPORT);return;}catch(RuntimeException ignored){}
        }
        exportConfigDirectory(source);
    }
    private void exportConfigDirectory(final File source) {
        configWork("导出到本机目录","config_export",false,new ConfigWork(){public Object run()throws Exception{
            File base=getExternalFilesDir(null);if(base==null)throw new IOException("本机存储不可用；应用内备份仍然保留");
            File output=new File(new File(base,"backups"),source.getName());ConfigStore.atomic(output,ConfigStore.read(source));return output;
        }},new ConfigResult(){public void done(Object value){pendingBackupName=null;configDialog(new GlassDialog.Builder(MainActivity.this,scene,unit,largeText).setTitle("已导出到本机目录")
            .setMessage(((File)value).getAbsolutePath()+"\n\n可用文件管理器复制到 U 盘或电脑。此目录也可能随卸载应用删除，请另存留备。")
            .setPositiveButton("知道了",null),"config_export");}});
    }
    private void pickConfigFile() {
        Intent intent=new Intent(Build.VERSION.SDK_INT>=19?"android.intent.action.OPEN_DOCUMENT":Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_LOCAL_ONLY,true);
        dismissDialog();try{startActivityForResult(intent,CONFIG_IMPORT);return;}catch(RuntimeException ignored){}
        if(Build.VERSION.SDK_INT>=19){try{intent.setAction(Intent.ACTION_GET_CONTENT);startActivityForResult(intent,CONFIG_IMPORT);return;}catch(RuntimeException ignored){}}
        configDialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("系统没有可用的文件选择器")
            .setMessage("仍可选择栖屏应用内保存的备份；也可以安装支持文件选择的文件管理器后重试。")
            .setPositiveButton("应用内备份",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){chooseLocalConfig();}}).setNegativeButton("返回",null),"config_import");
    }
    private void chooseLocalConfig() {
        final List<File> files=configStore.backups();
        if(files.isEmpty()){configDialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("暂无应用内备份").setMessage("先在桌面配置中选择「备份配置」，或从文件管理器选择已有的 .qiping 文件。")
            .setPositiveButton("知道了",null),"config_import");return;}
        String[] names=new String[files.size()];SimpleDateFormat date=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA);
        for(int i=0;i<files.size();i++)names[i]="备份 "+(i+1)+" · "+date.format(new Date(files.get(i).lastModified()));
        configDialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("应用内备份").setSidePanel().setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){readConfig(Uri.fromFile(files.get(which)));}}).setNegativeButton("取消",null),"config_import");
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==DIAGNOSTIC_EXPORT){
            final String report=pendingDiagnosticReport;pendingDiagnosticReport=null;
            if(result!=RESULT_OK||data==null||data.getData()==null||report==null){showDiagnostics();return;}
            final Uri uri=data.getData();worker.execute(new Runnable(){public void run(){String failure=null;try{
                if(!"content".equals(uri.getScheme()))throw new IOException("请选择系统文件位置");
                OutputStream out=getContentResolver().openOutputStream(uri,"w");if(out==null)throw new IOException("所选位置无法写入");
                try{out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();}finally{out.close();}
            }catch(Exception error){failure="导出失败，请检查所选位置或剩余空间";}
                final String message=failure;handler.post(new Runnable(){public void run(){if(destroyed)return;toast(message==null?"诊断文件已导出":message);if(resumed)showDiagnostics();}});
            }});return;
        }
        if(request==WALLPAPER_IMPORT){int category=wallpaperScene;wallpaperScene=-1;if(category<0||category>5)return;if(result==RESULT_OK&&data!=null&&data.getData()!=null)importWallpaper(category,data.getData());else showWallpaperActions(category);return;}
        if(request!=CONFIG_EXPORT&&request!=CONFIG_IMPORT)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){pendingBackupName=null;configPanel(request==CONFIG_EXPORT?"config_export":"config_import");return;}
        final Uri uri=data.getData();
        if(request==CONFIG_IMPORT){readConfig(uri);return;}
        final String name=pendingBackupName;pendingBackupName=null;
        configWork("写入备份文件","config_export",false,new ConfigWork(){public Object run()throws Exception{
            // A picker is external input: never let a returned file:// write with our app's UID.
            // The no-picker fallback writes only to our own fixed backups directory above.
            if(!"content".equals(uri.getScheme())||!uri.isHierarchical()||uri.getAuthority()==null||uri.getAuthority().length()==0)throw new IOException("请选择系统文件保存位置");
            byte[] bytes=ConfigStore.read(configStore.file(name));ConfigBackup.decode(bytes);
            OutputStream out=getContentResolver().openOutputStream(uri,"w");if(out==null)throw new IOException("无法打开所选位置");
            try{out.write(bytes);out.flush();}finally{out.close();}return null;
        }},new ConfigResult(){public void done(Object ignored){configDialog(new GlassDialog.Builder(MainActivity.this,scene,unit,largeText).setTitle("备份文件已导出").setMessage("文件已写入刚才选择的位置，应用内副本也保留。")
            .setPositiveButton("完成",null),"config_export");}});
    }
    private void readConfig(final Uri uri) {
        configWork("读取备份文件","config_import",false,new ConfigWork(){public Object run()throws Exception{
            if(!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme()))throw new IOException("文件位置不受支持");
            InputStream in=getContentResolver().openInputStream(uri);try{return ConfigBackup.decode(ConfigBackup.read(in));}finally{if(in!=null)in.close();}
        }},new ConfigResult(){public void done(Object value){previewConfig((ConfigBackup)value,false);}});
    }
    private String backupApp(String pkg) {
        if(pkg.length()==0)return "未绑定";AppEntry app=byPackage(pkg);if(app!=null)return app.label.replace('\n',' ');
        try{android.content.pm.ApplicationInfo info=getPackageManager().getApplicationInfo(pkg,0);return (info.enabled?"已安装，入口待核对：":"已停用：")+pkg;}
        catch(android.content.pm.PackageManager.NameNotFoundException error){return "未安装："+pkg;}
    }
    private void previewConfig(final ConfigBackup value,final boolean undo) {
        StringBuilder text=new StringBuilder(undo?"将还原最近一次恢复前的设置。\n":"将替换以下桌面设置。\n");
        if(value.created>0)text.append("备份时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(value.created))).append('\n');
        for(int i=0;i<4;i++)text.append('\n').append(ShortcutStore.NAMES[i]).append("：").append(backupApp(value.categories.get(i)));
        text.append("\n\n收藏顺序（").append(value.collection.size()).append(" 个）：");
        if(value.collection.isEmpty())text.append("空列表");for(int i=0;i<value.collection.size();i++)text.append('\n').append(i+1).append(". ").append(backupApp(value.collection.get(i)));
        text.append("\n\n字号：").append(DisplayPreferences.FONT_NAMES[value.font]).append("\n边距：").append(DisplayPreferences.MARGIN_NAMES[value.margin])
            .append("\n界面动效：").append(value.reducedMotion?"关闭":"开启").append("\n性能：").append(value.performance==1?"轻量模式":"自动适配")
            .append("\n长辈布局：").append(value.simpleHome?"开启":"关闭").append("\n布局锁定：").append(value.locked?"锁定":"未锁")
            .append("\n\n未安装应用只保留绑定或收藏标识，不会安装应用。\n最近打开、开机开关和本次启动记录保持本机状态。本次恢复不会打开直播；若本机已开启开机直播，下次重启将使用恢复后的直播绑定。");
        text.append(value.extended?"\n\n包含首页排列与隐藏、应用排列与隐藏，以及屏保等待时间。":"\n\n此为旧版备份，首页排列、应用隐藏和屏保偏好保持本机状态。");
        text.append("\n本地壁纸图片不随配置文件迁移，保持本机状态。");
        configDialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(undo?"预览撤销内容":"预览恢复内容").setMessage(text.toString())
            .setPositiveButton(undo?"确认撤销":"确认恢复",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                if(!layoutEditable()){configPanel(undo?"config_undo":"config_import");return;}
                configWork(undo?"还原恢复前配置":"应用配置",undo?"config_undo":"config_import",true,new ConfigWork(){public Object run()throws Exception{if(undo)configStore.undo();else configStore.restore(value);return null;}},new ConfigResult(){public void done(Object ignored){
                    organizer.reload();icons.clear();render(focusedKey);configPanel(undo?"config_undo":"config_import");toast(undo?"已撤销上次恢复":"配置已恢复，可在桌面配置中撤销");
                }});
            }}).setNegativeButton("取消",null),undo?"config_undo":"config_import");
    }
    private void showConfigRecovery() {
        configRecoveryError="上次配置恢复未完成。请先还原原配置；若空间不足，可进入系统设置清理后再重试。";
        Dialog warning=new GlassDialog.Builder(this,scene,unit,largeText).setTitle("配置需要还原").setMessage(configRecoveryError)
            .setPositiveButton("重试还原",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                configWork("还原原配置","config_import",true,new ConfigWork(){public Object run()throws Exception{configStore.recover();return null;}},new ConfigResult(){public void done(Object ignored){configRecoveryError=null;organizer.reload();render(focusedKey);configPanel("config_import");}});
            }}).setNegativeButton("系统设置",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){openSettings();}}).create();
        warning.setCancelable(false);warning.setCanceledOnTouchOutside(false);showDialog(warning);
    }

    private void buildDeviceInfo() {
        StageLayout layout=new StageLayout(this);surface.addView(layout,new FrameLayout.LayoutParams(-1,-1));activeLayer=layout;
        int margin=clamp(preferences.getInt("margin",1),0,2);
        float edge=new float[]{30,48,66}[margin],top=margin==2?36:24;
        GlassButton back=glassButton("back","back","返回桌面设置",new Runnable(){@Override public void run(){onBackPressed();}});
        layout.place(back,edge,top-4,44,44);layout.place(text("栖屏",16,TEXT,false),edge+58,top+4,120,28);
        clockText=text("00:00",16,TEXT,false);clockText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        layout.place(clockText,960-edge-124,top+4,64,28);
        GlassButton refresh=glassButton("device_refresh","refresh","刷新设备信息",new Runnable(){@Override public void run(){
            deviceInfo=DeviceInfo.read(MainActivity.this);render("device_refresh");toast("设备信息已更新");
        }});
        layout.place(refresh,960-edge-44,top-4,44,44);
        List<View> header=new ArrayList<View>();header.add(back);header.add(refresh);focusRows.add(header);
        TextView title=text("设备与性能",largeText?40:36,TEXT,true);title.setShadowLayer(u(4),0,u(2),0x66000000);
        layout.place(title,edge+10,top+51,700,55);
        layout.place(text("当前设备的信息与桌面运行方式",largeText?16:14,GlassPanel.SECONDARY,false),edge+12,top+107,760,23);
        DisplayMetrics metrics=new DisplayMetrics();getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final String[] names={"设备型号","安卓版本","显示区域","运行内存","存储空间","性能适配"};
        final String[] values={DeviceInfo.value(Build.MODEL),"Android "+DeviceInfo.value(Build.VERSION.RELEASE),
                metrics.widthPixels+" × "+metrics.heightPixels,"可用 "+DeviceInfo.bytes(deviceInfo.availableRam),
                "可用 "+DeviceInfo.bytes(deviceInfo.availableStorage),performance.light?"轻量模式":"标准模式"};
        boolean manual=preferences.getInt("performance_mode",0)==1;
        final String[] notes={DeviceInfo.value(Build.MANUFACTURER)+" · "+DeviceInfo.value(Build.BRAND),
                "API "+Build.VERSION.SDK_INT+" · 栖屏 "+launcherVersion,"当前桌面绘制区域 · "+metrics.densityDpi+" dpi",
                "系统总计 "+DeviceInfo.bytes(deviceInfo.totalRam),"数据分区总计 "+DeviceInfo.bytes(deviceInfo.totalStorage),
                (manual?"手动选择":"自动适配")+(performance.light?" · 减少图片缓存":" · 保留背景细节")};
        final String[] details={"型号："+values[0]+"\n制造商："+DeviceInfo.value(Build.MANUFACTURER)+"\n品牌："+DeviceInfo.value(Build.BRAND),
                values[1]+"\nAPI 级别："+Build.VERSION.SDK_INT+"\n系统构建："+DeviceInfo.value(Build.DISPLAY)+"\n栖屏版本："+launcherVersion,
                values[2]+" 像素\n显示密度："+metrics.densityDpi+" dpi\n\n这里显示系统分配给桌面的绘制区域，可能与电视面板的物理分辨率不同。",
                "系统总计："+DeviceInfo.bytes(deviceInfo.totalRam)+"\n当前可用："+DeviceInfo.bytes(deviceInfo.availableRam)+
                        "\n应用堆上限："+(deviceInfo.heapMb>0?deviceInfo.heapMb+" MB":"未能读取")+
                        "\n内存状态："+(deviceInfo.memoryPressure?"系统提示内存紧张":"未收到内存紧张提示")+
                        "\n\n系统可见总内存可能小于标称容量；当前可用值会随其他应用运行而变化。",
                "数据分区总计："+DeviceInfo.bytes(deviceInfo.totalStorage)+"\n当前可用："+DeviceInfo.bytes(deviceInfo.availableStorage)+
                        "\n\n此处为应用所在数据分区，不是电视标称的全部存储，也不包含外接 U 盘。",""};
        float width=(960-2*edge)/2;
        for(int r=0;r<3;r++) {
            List<View> row=new ArrayList<View>();
            for(int c=0;c<2;c++) {
                final int index=r*2+c;
                DeviceCard card=new DeviceCard(this,scene,names[index],values[index],notes[index],largeText,unit);
                homeControl(card,"device"+index,new Runnable(){@Override public void run(){
                    if(index==5)choosePerformance();
                    else dialog(new GlassDialog.Builder(MainActivity.this,scene,unit,largeText).setTitle(names[index])
                            .setMessage(details[index]).setPositiveButton("关闭",null));
                }});
                layout.place(card,edge+c*width,top+132+r*94,width,92);row.add(card);
            }
            focusRows.add(row);
        }
        float footerY=margin==2?466:470;
        layout.place(text("确定查看详情  ·  右上角刷新",largeText?14:12,GlassPanel.SECONDARY,false),edge+12,footerY+3,410,32);
        GlassButton usage=new GlassButton(this,scene,null,text("使用说明",largeText?16:14,TEXT,false));
        homeControl(usage,"device_usage",new Runnable(){@Override public void run(){showUsage();}});
        usage.setContentDescription("使用说明");layout.place(usage,960-edge-142,footerY-3,142,44);
        GlassButton diagnostic=new GlassButton(this,scene,null,text("运行诊断",largeText?16:14,TEXT,false));
        homeControl(diagnostic,"device_diagnostics",new Runnable(){public void run(){showDiagnostics();}});
        diagnostic.setContentDescription("运行诊断");layout.place(diagnostic,960-edge-296,footerY-3,142,44);
        List<View> footer=new ArrayList<View>();footer.add(diagnostic);footer.add(usage);focusRows.add(footer);
    }

    private void recordStartupDiagnostic(){
        if(diagnostics==null||startupState==null)return;
        String value=startupState.statusBoot()+" · "+startupState.status()+" · 唤醒："+(wakeState==null?"未初始化":wakeState.status());
        if(!value.equals(lastDiagnosticStartup)){lastDiagnosticStartup=value;diagnostics.event("开机直播",value);}
    }
    private String memorySummary(){
        Runtime runtime=Runtime.getRuntime();
        return "Java 堆 "+DeviceInfo.bytes(runtime.totalMemory()-runtime.freeMemory())+" / "+DeviceInfo.bytes(runtime.maxMemory())
            +"\n原生堆 "+DeviceInfo.bytes(android.os.Debug.getNativeHeapAllocatedSize())
            +"\n壁纸缓存 "+(scene==null?0:scene.cachedSceneCount())+" 张 · "+DeviceInfo.bytes(scene==null?0:scene.cachedBytes())
            +"\n图标缓存 "+icons.size()+" 张 · 待解码 "+(scene==null?0:scene.queuedLoads())+" 项";
    }
    private void sampleMemory(boolean force){
        long now=SystemClock.elapsedRealtime();if(!force&&lastMemorySample>0&&now-lastMemorySample<300000)return;lastMemorySample=now;
        diagnostics.event("前台内存",memorySummary());
    }
    private String diagnosticReport(){
        return "栖屏运行诊断 · "+launcherVersion+"\n生成时间："+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date())
            +"\n设备："+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE
            +"\n本进程运行："+bootDuration(SystemClock.elapsedRealtime()-diagnostics.processStarted)
            +"\n系统运行："+bootDuration(SystemClock.elapsedRealtime())+"\n性能："+(performance.light?"轻量模式":"标准模式")
            +"\n\n"+memorySummary()+"\n玻璃组件 "+(scene==null?0:scene.registeredGlassCount())+" 个"
            +"\n\n开机直播："+(startupState.enabled()?"开启":"关闭")+"\n默认桌面："+(isDefaultHome()?"栖屏":"尚未确认为栖屏")
            +"\n当前启动标识："+BootSession.current(this)+"\n最近启动状态："+startupState.status()
            +"\n待机唤醒直播："+(wakeState.enabled()?"开启":"关闭")+"\n唤醒状态："+wakeState.status()+"\n电源监听："+wakeMonitor.observation()
            +"\n\n最近记录（最多 100 条 / 48 KiB）：\n"+diagnostics.snapshot()
            +"\nJava 堆、原生堆及缓存有重叠，不能相加作为总内存。缓存不含绘制中过渡帧。\n打开请求成功不代表直播已经播放。记录只涵盖栖屏自身事件，不读取外部应用日志。\n系统强制结束、断电、原生崩溃或无响应可能没有记录；没有异常记录不等于没有故障。\n";
    }
    private void showDiagnostics(){
        if(destroyed||!resumed)return;
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("运行诊断")
            .setMessage("本机保留最近 100 条关键记录。\n可查看运行状态，或导出文件供排查。")
            .setItems(new String[]{"查看当前状态","查看最近记录","导出诊断文件","清空诊断记录"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                if(which==0){sampleMemory(true);diagnosticText("当前运行状态",diagnosticReport().split("最近记录（")[0]+"\n内存读数不是应用总占用；查看最近记录可比较前台采样。\n每 5 分钟仅在桌面前台采样，离开桌面后暂停。");}
                else if(which==1)diagnosticText("最近运行记录",diagnostics.snapshot());
                else if(which==2)exportDiagnostics();
                else menu(new GlassDialog.Builder(MainActivity.this,scene,unit,largeText).setTitle("清空诊断记录？").setMessage("清空后无法恢复。桌面设置、壁纸和开机配置不受影响。")
                    .setPositiveButton("确认清空",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){diagnostics.clear(new RunDiagnostics.Completion(){public void done(final boolean saved){handler.post(new Runnable(){public void run(){if(destroyed)return;toast(saved?"诊断记录已清空":"清空失败，请稍后重试");if(resumed)showDiagnostics();}});}});}})
                    .setNegativeButton("取消",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){showDiagnostics();}}),new Runnable(){public void run(){showDiagnostics();}});
            }}),null);
    }
    private void diagnosticText(String title,String message){
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(title).setMessage(message).setPositiveButton("返回诊断",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){showDiagnostics();}}),new Runnable(){public void run(){showDiagnostics();}});
    }
    private void exportDiagnostics(){
        pendingDiagnosticReport=diagnosticReport();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"Qiping_diagnostics_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.CHINA).format(new Date())+".txt");
        try{startActivityForResult(intent,DIAGNOSTIC_EXPORT);}catch(RuntimeException error){pendingDiagnosticReport=null;diagnosticText("无法导出文件","此设备没有可用的系统文件保存界面。可返回诊断，查看并拍下当前状态或最近记录。");}
    }

    private void choosePerformance() {
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("性能适配")
                .setSingleChoiceItems(new String[]{"自动适配（推荐）","轻量模式 · 减少图片缓存"},
                        clamp(preferences.getInt("performance_mode",0),0,1),new DialogInterface.OnClickListener(){
                    @Override public void onClick(DialogInterface d,int which) {
                        preferences.edit().putInt("performance_mode",which).apply();d.dismiss();render("device5");
                    }
                }).setNegativeButton("取消",null));
    }

    private void buildStartupSettings() {
        StageLayout layout=new StageLayout(this);surface.addView(layout,new FrameLayout.LayoutParams(-1,-1));activeLayer=layout;
        int margin=clamp(preferences.getInt("margin",1),0,2);
        float edge=new float[]{30,48,66}[margin],top=margin==2?36:24;
        GlassButton back=glassButton("back","back","返回桌面设置",new Runnable(){@Override public void run(){onBackPressed();}});
        layout.place(back,edge,top-4,44,44);layout.place(text("栖屏",16,TEXT,false),edge+58,top+4,120,28);
        clockText=text("00:00",16,TEXT,false);clockText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        layout.place(clockText,960-edge-90,top+4,80,28);
        List<View> header=new ArrayList<View>();header.add(back);focusRows.add(header);
        TextView title=text("开机与桌面",largeText?40:36,TEXT,true);title.setShadowLayer(u(4),0,u(2),0x66000000);
        layout.place(title,edge+10,top+51,700,55);
        layout.place(text("系统重启和待机唤醒可分别设置",largeText?16:14,GlassPanel.SECONDARY,false),edge+12,top+107,780,23);
        AppEntry live=byPackage(shortcuts.get(1));
        String target=live!=null?live.label:loading?"正在读取应用…":shortcuts.get(1).length()==0?"请选择已安装的应用":"应用不可用，请重新选择";
        String[] titles={"默认桌面","开机自动打开直播","直播应用","立即试播"};
        String[] subtitles={isDefaultHome()?"已设为栖屏":"需先将栖屏设为默认桌面",startupState.enabled()?"已开启 · 下次重启生效":"已关闭 · 开机停留在桌面",target,"现在打开直播应用，验证是否正常"};
        String[] glyphs={"home","motion","live","film"};
        float slotWidth=(960-2*edge)/2,gridY=top+136;
        for(int r=0;r<2;r++) {
            List<View> row=new ArrayList<View>();
            for(int c=0;c<2;c++) {
                final int action=r*2+c;
                int toggle=action==1?(startupState.enabled()?1:0):-1;
                SettingsTile tile=new SettingsTile(this,scene,glyphs[action],titles[action],subtitles[action],toggle,largeText,motion,unit);
                homeControl(tile,"startup"+action,new Runnable(){@Override public void run(){startupSetting(action);}});
                layout.place(tile,edge+c*slotWidth,gridY+r*104,slotWidth,104);row.add(tile);
            }
            focusRows.add(row);
        }
        GlassButton wake=new GlassButton(this,scene,null,text("待机唤醒也打开直播："+(wakeState.enabled()?"已开启":"已关闭"),largeText?19:17,TEXT,false));
        homeControl(wake,"startup_wake",new Runnable(){public void run(){startupSetting(4);}});
        wake.setContentDescription("待机唤醒也打开直播");layout.place(wake,edge+6,top+354,948-2*edge,46);
        List<View> wakeRow=new ArrayList<View>();wakeRow.add(wake);focusRows.add(wakeRow);
        layout.place(text("桌面就绪后等待 3 秒，按返回取消；每次只打开一次。",largeText?15:13,GlassPanel.SECONDARY,false),edge+12,top+407,820,25);
        layout.place(text("遥控器关开请开启上方的待机唤醒选项。",largeText?15:13,GlassPanel.SECONDARY,false),edge+12,top+449,620,26);
        GlassButton status=new GlassButton(this,scene,null,text("启动状态",largeText?16:14,TEXT,false));
        homeControl(status,"startup_status",new Runnable(){@Override public void run(){showStartupStatus();}});
        status.setContentDescription("启动状态");layout.place(status,960-edge-142,top+443,142,40);
        List<View> footer=new ArrayList<View>();footer.add(status);focusRows.add(footer);
    }

    private void showStartupStatus() {
        String token=BootSession.current(this);
        AppEntry live=byPackage(shortcuts.get(1));
        String target=live!=null?live.label:shortcuts.get(1).length()==0?"尚未选择":shortcuts.get(1);
        long sinceBoot=SystemClock.elapsedRealtime(),recorded=startupState.statusTime();
        String message="系统重启自动打开："+(startupState.enabled()?"已开启":"已关闭")
                +"\n待机唤醒自动打开："+(wakeState.enabled()?"已开启":"已关闭")
                +"\n默认桌面："+(isDefaultHome()?"栖屏":"系统尚未确认为栖屏")
                +"\n直播应用："+target
                +"\n距系统启动："+bootDuration(sinceBoot)
                +"\n\n最近记录"+(token.equals(startupState.statusBoot())?"（本次系统启动）":"（此前记录）")
                +"\n"+startupState.status()
                +(recorded<0?"":"\n记录于开机后 "+bootDuration(recorded))
                +"\n\n当前启动标识："+token
                +"\n已处理标识："+startupState.handledBoot()
                +"\n当前入口："+(isHomeIntent(getIntent())?"系统桌面入口":"普通应用入口")
                +"\n\n最近唤醒记录\n"+wakeState.status()
                +"\n电源监听："+wakeMonitor.observation()
                +"\n\n遥控器关屏再亮屏可能只是待机唤醒。请开启「待机唤醒也打开直播」，回到首页后再关开测试。若唤醒后仍在其他应用，栖屏不会从后台强行切换；等待超过 60 秒后不补启动。"
                +"\n\n「已向系统发送打开请求」只表示请求已发出，不代表直播应用已经播放。可先用「立即试播」验证绑定。直播绑定与首页同步。";
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("启动状态")
                .setMessage(message).setPositiveButton("关闭",null).setNegativeButton("运行诊断",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){showDiagnostics();}}));
    }

    private static String bootDuration(long millis) {
        long seconds=Math.max(0,millis)/1000;
        return (seconds/3600)+" 小时 "+(seconds/60%60)+" 分 "+(seconds%60)+" 秒";
    }

    private void startupSetting(int action) {
        if(action==0){defaultHome();return;}
        if(action==2){beginStartupPick();return;}
        if(action==3) {
            AppEntry live=byPackage(shortcuts.get(1));
            if(live!=null)launch(live);
            else toast(loading?"正在读取应用，请稍后再试":"请先选择可打开的直播应用");
            return;
        }
        boolean forWake=action==4;
        boolean enable=forWake?!wakeState.enabled():!startupState.enabled();
        if(enable&&byPackage(shortcuts.get(1))==null) {
            if(loading){toast("正在读取应用，请稍后再试");return;}
            dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("先选择直播应用")
                    .setMessage("选择电视上已经安装的直播应用，然后开启自动打开。选择的应用也会用于首页「直播」入口。")
                    .setPositiveButton("选择应用",new DialogInterface.OnClickListener(){
                        @Override public void onClick(DialogInterface d,int which){beginStartupPick();}
                    }).setNegativeButton("取消",null));
            return;
        }
        if(!(forWake?wakeState.setEnabled(enable,SystemClock.elapsedRealtime()):startupState.setEnabled(enable,BootSession.current(this)))) {
            toast("设置未能保存，请检查设备存储后重试");return;
        }
        recordStartupDiagnostic();
        pauseStartup();render(forWake?"startup_wake":"startup1");
        toast(enable?(isDefaultHome()?(forWake?"已开启，下次待机唤醒回到首页后生效":"已开启，下次系统重启时自动打开直播"):"已开启；请先将栖屏设为默认桌面"):(forWake?"已关闭待机唤醒直播":"已关闭开机直播"));
    }

    private void beginStartupPick() {
        if(!layoutEditable())return;
        pickingSlot=1;pickingForStartup=true;screen=APPS;page=0;render("app0");
    }

    private static boolean isHomeIntent(Intent intent) {
        return intent!=null&&Intent.ACTION_MAIN.equals(intent.getAction())&&intent.hasCategory(Intent.CATEGORY_HOME);
    }

    private void checkStartup() {
        if(destroyed||startupFlow==null)return;
        handler.removeCallbacks(startupTick);
        String token=BootSession.current(this);
        long now=SystemClock.elapsedRealtime();
        AppEntry live=scanError?null:byPackage(shortcuts.get(1));
        if(childMode()&&!parental.mayLaunch(shortcuts.get(1),System.currentTimeMillis())){
            String reason="未成年模式：直播应用未获允许或不在可用时段，本次自动打开已跳过";
            startupState.skip(token,reason,now);
            if(wakeState.waiting()){wakeState.cancel(now);wakeState.record(reason,now);}
            pauseStartup();recordStartupDiagnostic();return;
        }
        String waitReason=!wakeMonitor.interactive()?"等待电视唤醒":!resumed||!hasWindowFocus()||screen!=HOME||currentDialog!=null?StartupFlow.WAIT_FOREGROUND
                :!isDefaultHome()?StartupFlow.WAIT_DEFAULT:loading?StartupFlow.WAIT_SCAN
                :scanError?StartupFlow.WAIT_SCAN_ERROR:live==null?StartupFlow.WAIT_TARGET:null;
        boolean waking=wakeState.pending(token,now);
        if(waking)startupState.skip(token,"本次由待机唤醒流程处理",now);
        int result=waking?wakeFlow.poll(token,now,waitReason):startupFlow.poll(token,now,isHomeIntent(getIntent()),waitReason);
        recordStartupDiagnostic();
        boolean wasCounting=startupTarget!=null;
        startupTarget=result==StartupFlow.COUNTDOWN?live:null;
        wakeCountdown=waking&&startupTarget!=null;
        if(result==StartupFlow.COUNTDOWN) {
            startupDeadline=waking?wakeFlow.deadline():startupFlow.deadline();
            if(!wasCounting) {
                if(!childMode())selectCategory(1,false);
                View tile=controls.get(homeFocus());if(tile!=null)tile.requestFocus();
            }
        }
        if(wasCounting||startupTarget!=null)updateHero();
        if(result==StartupFlow.LAUNCH) {
            boolean opened=live!=null&&launch(live);
            String message=opened?"已向系统发送打开请求："+live.label:"系统未能打开直播应用，请先用「立即试播」检查";
            if(waking)wakeState.record(message+"（待机唤醒）",now);else startupState.record(token,message,now);
            recordStartupDiagnostic();
        } else if(result==StartupFlow.WAIT||result==StartupFlow.COUNTDOWN) {
            // Boot-time package discovery may initially be empty. Retry only this scan, never startActivity.
            if(StartupFlow.WAIT_TARGET.equals(waitReason)||StartupFlow.WAIT_SCAN_ERROR.equals(waitReason)) {
                if(startupNextScanAt==0)startupNextScanAt=now+1500;
                if(startupScanRetries<3&&now>=startupNextScanAt) {
                    startupScanRetries++;
                    startupNextScanAt=now+3000L*(startupScanRetries+1);
                    refreshApps();
                }
            }
            if(resumed)handler.postDelayed(startupTick,result==StartupFlow.COUNTDOWN?250:500);
        }
    }

    private void pauseStartup() {
        handler.removeCallbacks(startupTick);
        if(startupFlow!=null)startupFlow.suspend();
        if(wakeFlow!=null)wakeFlow.suspend();
        boolean wasCounting=startupTarget!=null;
        startupTarget=null;
        wakeCountdown=false;
        if(wasCounting)updateHero();
    }

    private void cancelStartup(boolean notify) {
        boolean wasPending=startupTarget!=null;
        if(startupFlow!=null)startupFlow.cancel(BootSession.current(this),SystemClock.elapsedRealtime());
        if(wakeState!=null)wakeState.cancel(SystemClock.elapsedRealtime());
        pauseStartup();
        recordStartupDiagnostic();
        if(notify&&wasPending)toast("已取消本次自动打开直播");
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int wakeCode=event.getKeyCode();
        if(wakeKey==wakeCode&&wakeKey!=0){if(event.getAction()==KeyEvent.ACTION_UP)wakeKey=0;return true;}
        if(idleView!=null&&isWakeKey(wakeCode)){if(event.getAction()==KeyEvent.ACTION_DOWN){exitScreensaver();wakeKey=wakeCode;noteInput();}return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN)noteInput();
        if(event.getAction()==KeyEvent.ACTION_UP&&event.getKeyCode()==KeyEvent.KEYCODE_BACK&&cancelBackUp) {
            cancelBackUp=false;return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&!isPowerTransitionKey(event.getKeyCode())&&startupFlow!=null&&(!startupFlow.interacted()||startupTarget!=null||wakeState.waiting())) {
            boolean cancelBack=startupTarget!=null&&event.getKeyCode()==KeyEvent.KEYCODE_BACK;
            cancelStartup(true);
            if(cancelBack){cancelBackUp=true;return true;}
        }
        int code=event.getKeyCode();
        // A dialog owns navigation even before WindowManager finishes transferring input focus.
        // Its own Window dispatches directly to Dialog; events still reaching this Activity must not click behind it.
        if(currentDialog!=null&&isWakeKey(code))return true;
        if(currentDialog==null) {
            if(code>=KeyEvent.KEYCODE_DPAD_UP&&code<=KeyEvent.KEYCODE_DPAD_RIGHT) {
                if(event.getAction()==KeyEvent.ACTION_UP){if(heldPageDirection==code)heldPageDirection=0;return true;}
                if(event.getAction()==KeyEvent.ACTION_DOWN) {
                    cancelConfirm();
                    if(event.getRepeatCount()==0)heldPageDirection=0;
                    if(focusReady&&heldPageDirection!=code)moveDirection(code);
                    return true;
                }
            }
            if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A) {
                if(event.getAction()==KeyEvent.ACTION_DOWN) {
                    if(event.getRepeatCount()==0&&focusReady) {
                        cancelConfirm();confirmKey=code;confirmTarget=getCurrentFocus();
                        if(confirmTarget!=null){confirmTarget.setPressed(true);handler.postDelayed(longConfirm,550);}
                    }
                    return true;
                }
                if(event.getAction()==KeyEvent.ACTION_UP) {
                    View target=confirmTarget;boolean click=confirmKey==code&&!confirmLong&&!event.isCanceled();cancelConfirm();
                    if(click&&focusReady&&target!=null&&target==getCurrentFocus())target.performClick();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(idleView!=null&&event.getAction()==MotionEvent.ACTION_DOWN){exitScreensaver();wakeTouch=true;noteInput();}
        if(wakeTouch){if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)wakeTouch=false;return true;}
        if(event.getAction()==MotionEvent.ACTION_DOWN)noteInput();
        if(event.getAction()==MotionEvent.ACTION_DOWN&&startupFlow!=null&&(!startupFlow.interacted()||startupTarget!=null||wakeState.waiting()))cancelStartup(true);
        return super.dispatchTouchEvent(event);
    }

    private static boolean isPowerTransitionKey(int code){return code==KeyEvent.KEYCODE_POWER||code==KeyEvent.KEYCODE_TV_POWER||code==KeyEvent.KEYCODE_SLEEP||code==KeyEvent.KEYCODE_WAKEUP;}

    private void choosePreference(String title, String[] labels, final String key, int defaultValue, final String focus) {
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(title).setSingleChoiceItems(labels, preferences.getInt(key,defaultValue),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d,int which) {
                        preferences.edit().putInt(key,which).apply(); d.dismiss(); render(focus);
                    }
                }).setNegativeButton("取消",null));
    }

    private boolean isOrganizedGrid(){return screen==RECENT||screen==COLLECTION;}
    private boolean isAppGrid(){return childMode()&&screen==HOME||screen==APPS||screen==UPDATES||isOrganizedGrid();}

    private void stopPackageReceiver() {
        handler.removeCallbacks(packageRefresh);
        if(packageReceiverRegistered){try{unregisterReceiver(packageReceiver);}catch(RuntimeException ignored){}packageReceiverRegistered=false;}
    }

    private void cancelConfirm() {
        handler.removeCallbacks(longConfirm);
        if(confirmTarget!=null)confirmTarget.setPressed(false);
        confirmTarget=null;confirmKey=0;confirmLong=false;
    }

    private void rememberFocus(String key) {
        focusedKey=key;
        View view=controls.get(key);
        if(view!=null&&!focusRows.isEmpty()&&!focusRows.get(0).contains(view))rememberedFocus[screen]=key;
        restoreHeaderDown();
    }

    private void restoreHeaderDown() {
        if(screen==HOME&&!childMode()||focusRows.size()<2)return;
        View last=controls.get(rememberedFocus[screen]);
        if(last==null||focusRows.get(0).contains(last))return;
        for(View view:focusRows.get(0))view.setNextFocusDownId(last.getId());
    }

    private void moveDirection(int code) {
        View current=getCurrentFocus();Object value=current==null?null:current.getTag();
        if(current==null){View fallback=controls.get(isAppGrid()?"refresh":screen==HOME?homeFocus():"back");if(fallback!=null)fallback.requestFocus();return;}
        Tag tag=value instanceof Tag?(Tag)value:null;
        if(screen==HOME&&tag!=null&&tag.slot>=0&&(code==KeyEvent.KEYCODE_DPAD_DOWN||(code==KeyEvent.KEYCODE_DPAD_RIGHT&&tag.slot==workspace.home(true)[workspace.home(true).length-1]))) {
            go(APPS);heldPageDirection=code;return;
        }
        if(isAppGrid()&&tag!=null&&tag.entry!=null&&(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int index=gridIndex(tag.key);int target=GridNavigation.horizontal(index,gridApps.size(),code==KeyEvent.KEYCODE_DPAD_RIGHT);
            if(target==GridNavigation.BEFORE_FIRST) {
                if(pickingSlot<0&&!pickingCollection){onBackPressed();heldPageDirection=code;}
            }else if(target>=0&&target/PAGE_SIZE!=page) {
                page=target/PAGE_SIZE;render("app"+target);heldPageDirection=code;
            }else if(target>=0) {View view=controls.get("app"+target);if(view!=null)view.requestFocus();}
            return;
        }
        int id=code==KeyEvent.KEYCODE_DPAD_UP?current.getNextFocusUpId():code==KeyEvent.KEYCODE_DPAD_DOWN?current.getNextFocusDownId():code==KeyEvent.KEYCODE_DPAD_LEFT?current.getNextFocusLeftId():current.getNextFocusRightId();
        View next=activeLayer.findViewById(id);if(next!=null&&next.isFocusable())next.requestFocus();
    }

    private static int gridIndex(String key) {
        if(key==null||!key.matches("app[0-9]+"))return -1;
        try{return Integer.parseInt(key.substring(3));}catch(NumberFormatException error){return -1;}
    }

    private void swipePage(boolean right) {
        if(!focusReady||!isAppGrid())return;
        int nextPage=page+(right?1:-1);
        if(nextPage<0){if(pickingSlot<0&&!pickingCollection)onBackPressed();return;}
        int index=gridIndex(focusedKey),row=index>=0?(index%PAGE_SIZE)/6:0;
        int target=GridNavigation.pageTarget(nextPage,row,gridApps.size(),right);
        if(target>=0){page=nextPage;render("app"+target);}
    }

    private String focusedPackage() {
        View view=controls.get(focusedKey);Object value=view==null?null:view.getTag();
        return value instanceof Tag&&((Tag)value).entry!=null?((Tag)value).entry.packageName:"";
    }

    private String gridFocusForPackage(String pkg) {
        if(!isAppGrid()||pkg.length()==0)return focusedKey;
        int index=0;
        for(AppEntry app:visibleGrid()) {
            if(pkg.equals(app.packageName)){page=index/PAGE_SIZE;return "app"+index;}index++;
        }
        int fallback=Math.min(Math.max(0,gridIndex(focusedKey)),Math.max(0,index-1));
        page=fallback/PAGE_SIZE;return "app"+fallback;
    }

    private int updateCount(){int count=0;for(AppEntry app:apps)if(updates.available(app)!=null)count++;return count;}
    private boolean slotHasUpdate(int slot){return slot==4?updateCount()>0:updates.available(byPackage(shortcuts.get(slot)))!=null;}

    private void applyBadges() {
        if(childMode())return;
        if(updates==null)return;
        for(int i=0;i<3;i++){View view=controls.get("simple"+i);if(view instanceof SimpleHomeTile)((SimpleHomeTile)view).setUpdateAvailable(slotHasUpdate(i==0?1:i==1?3:4));}
        for(int i=0;i<dockTiles.length;i++)if(dockTiles[i]!=null){boolean badge=slotHasUpdate(i);dockTiles[i].setUpdateAvailable(badge);dockTiles[i].setContentDescription(ShortcutStore.NAMES[i]+(badge?"，有更新":""));}
        for(View view:controls.values())if(view instanceof AppTileView) {
            Tag tag=(Tag)view.getTag();AppEntry actual=byPackage(tag.entry.packageName);
            boolean badge=updates.available(actual)!=null;((AppTileView)view).setUpdateAvailable(badge);
            view.setContentDescription(tag.entry.label+(shortcuts.contains(tag.entry.packageName)?"，已绑定首页入口":"")+(badge?"，有更新":""));
        }
    }

    private void applyUpdatesToUi() {
        if(destroyed||updates==null)return;
        if(!resumed){uiNeedsRefresh=true;return;}
        if(currentDialog!=null&&currentDialog.isShowing()){refreshAfterDialog=true;return;}
        if(confirmTarget!=null||!focusReady){handler.removeCallbacks(updateUi);handler.postDelayed(updateUi,180);return;}
        applyBadges();if(gridDetail!=null)gridDetail.setText(appsDetail());
        if(screen==UPDATES) {
            boolean different=gridApps.size()!=updateCount();
            if(!different)for(AppEntry app:gridApps)if(updates.available(byPackage(app.packageName))==null){different=true;break;}
            // Rebuild the empty-state message too when a check finishes; never steal a dialog's focus.
            if(different||gridApps.isEmpty())render(gridFocusForPackage(focusedPackage()));
        }
    }

    private void openUpdates() {
        if(childMode()){openParentalSettings();return;}
        updatesOrigin=screen==SETTINGS?SETTINGS:APPS;updatesOriginFocus=focusedKey;normalAppsPage=page;
        screen=UPDATES;page=0;render("app0");updates.check(false);
    }

    private void openApp(final AppEntry entry) {
        if(childMode()){launch(entry);return;}
        final UpdateRepository.Record update=updates.available(byPackage(entry.packageName));
        if(update==null){launch(entry);return;}
        String old=entry.versionName.length()>0?entry.versionName:String.valueOf(entry.versionCode);
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(entry.label+" · 有新版本")
                .setMessage("当前 "+old+" → "+update.versionName+"\n来自 "+updates.sourceName())
                .setItems(new String[]{"前往更新","打开现有版本"},new DialogInterface.OnClickListener(){
                    @Override public void onClick(DialogInterface d,int which){if(which==0)openStore(entry,true);else launch(entry);}
                }).setNegativeButton("稍后",null));
    }

    private void openStore(final AppEntry entry,boolean configuredOnly) {
        if(childMode()){openParentalSettings();return;}
        Intent trusted=updates.storeIntent(entry.packageName);
        if(trusted!=null) {
            if(start(trusted))return;
            dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("暂时无法打开更新")
                    .setMessage("没有找到此更新服务对应的应用商店，或商店不支持该应用的详情链接。可先打开应用，使用应用内更新。")
                    .setPositiveButton("打开应用",new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface d,int which){launch(entry);}})
                    .setNegativeButton("返回",null));return;
        }
        if(configuredOnly){toast("此应用暂未接入更新服务");return;}
        final Intent market=new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+entry.packageName));
        final List<ResolveInfo> stores=new ArrayList<ResolveInfo>();
        for(ResolveInfo item:getPackageManager().queryIntentActivities(market,0))if(item.activityInfo!=null&&item.activityInfo.enabled&&item.activityInfo.exported&&!getPackageName().equals(item.activityInfo.packageName))stores.add(item);
        if(stores.size()==1){market.setComponent(new ComponentName(stores.get(0).activityInfo.packageName,stores.get(0).activityInfo.name));if(start(market))return;}
        else if(stores.size()>1) {
            String[] names=new String[stores.size()];for(int i=0;i<names.length;i++)names[i]=stores.get(i).loadLabel(getPackageManager()).toString();
            dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("选择应用商店").setItems(names,new DialogInterface.OnClickListener(){
                @Override public void onClick(DialogInterface d,int which){ResolveInfo store=stores.get(which);if(!start(new Intent(market).setComponent(new ComponentName(store.activityInfo.packageName,store.activityInfo.name))))toast("暂时无法打开该应用商店");}
            }).setNegativeButton("取消",null));return;
        }
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("未找到可用的应用商店")
                .setMessage("此设备没有支持应用详情链接的商店。可以打开应用，在应用自身的设置中检查更新。")
                .setPositiveButton("打开应用",new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface d,int which){launch(entry);}}).setNegativeButton("返回",null));
    }

    private void wireFocus() {
        for(int r=0;r<focusRows.size();r++) {
            List<View> row=focusRows.get(r);
            for(int c=0;c<row.size();c++) {
                View v=row.get(c);
                v.setNextFocusLeftId(row.get(Math.max(0,c-1)).getId());
                v.setNextFocusRightId(row.get(Math.min(row.size()-1,c+1)).getId());
                v.setNextFocusUpId(r==0?v.getId():closest(v,focusRows.get(r-1)).getId());
                v.setNextFocusDownId(r==focusRows.size()-1?v.getId():closest(v,focusRows.get(r+1)).getId());
                v.setNextFocusForwardId(c<row.size()-1?row.get(c+1).getId()
                        :r<focusRows.size()-1?focusRows.get(r+1).get(0).getId():focusRows.get(0).get(0).getId());
            }
        }
        restoreHeaderDown();
    }

    private View closest(View origin,List<View> candidates) {
        int[] point=new int[2]; origin.getLocationOnScreen(point);
        float x=point[0]+origin.getWidth()/2f;
        float distance=Float.MAX_VALUE; View best=candidates.get(0);
        for(View candidate:candidates) {
            candidate.getLocationOnScreen(point);
            float d=Math.abs(x-point[0]-candidate.getWidth()/2f);
            if(d<distance) {distance=d;best=candidate;}
        }
        return best;
    }

    private void manage(Tag tag) {
        if(childMode()){openParentalSettings();return;}
        if(pickingSlot>=0||pickingCollection) { toast("请按确定键选择应用，返回键取消"); return; }
        if(tag.slot>=0) slotMenu(tag.slot);
        else if(tag.entry!=null){if(screen==COLLECTION)collectionMenu(tag.entry);else appMenu(tag.entry);}
    }

    private void slotMenu(final int slot) {
        if(!layoutEditable())return;
        if(slot>=4){go(SETTINGS);return;}
        final AppEntry entry=byPackage(shortcuts.get(slot));
        if(shortcuts.get(slot).length()==0){beginPick(slot);return;}
        final boolean available=updates.available(entry)!=null;
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(ShortcutStore.NAMES[slot]+"入口")
                .setItems(new String[]{available?"查看应用更新":"前往应用商店","更换应用","取消绑定","应用信息"},new DialogInterface.OnClickListener(){
                    @Override public void onClick(DialogInterface d,int which) {
                        if(which==0){if(entry!=null){if(available)openApp(entry);else openStore(entry,false);}else toast("应用已卸载或暂不可用");}
                        else if(which==1)beginPick(slot);
                        else if(which==2){shortcuts.put(slot,"");updateHero();applyBadges();toast("已取消绑定");}
                        else if(entry!=null)appInfo(entry);else toast("应用已卸载或暂不可用");
                    }
                }).setNegativeButton("取消",null));
    }
    private void appMenu(final AppEntry entry) {
        final boolean available=updates.available(byPackage(entry.packageName))!=null;
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(entry.label).setItems(new String[]{available?"查看应用更新":"前往应用商店","打开应用","设为首页入口","应用信息",organizer.contains(entry.packageName)?"移出收藏":"加入收藏","排列与隐藏"},
                new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface d,int which){
                    if(which==0){if(available)openApp(entry);else openStore(entry,false);}
                    else if(which==1)launch(entry);else if(which==3)appInfo(entry);else if(which==4)toggleCollection(entry);else if(which==5)showAppLayout(entry);else chooseCategory(entry);
                }}).setNegativeButton("取消",null));
    }
    private void chooseCategory(final AppEntry entry) {
        if(!layoutEditable())return;
        String[] names=new String[4];for(int i=0;i<4;i++)names[i]=ShortcutStore.NAMES[i];
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("将「"+entry.label+"」绑定到").setItems(names,new DialogInterface.OnClickListener(){
            @Override public void onClick(DialogInterface d,int which){shortcuts.put(which,entry.packageName);render(focusedKey);toast("已绑定到"+ShortcutStore.NAMES[which]);}
        }).setNegativeButton("取消",null));
    }
    private void beginPick(int slot){if(!layoutEditable())return;pickingCollection=false;pickingForStartup=false;pickingSlot=slot;screen=APPS;page=0;render("app0");}
    private void go(int destination){
        if(childMode()&&destination!=HOME){openParentalSettings();return;}
        if(destination==SETTINGS&&(screen==HOME||screen==APPS)){settingsOrigin=screen;settingsOriginPage=page;settingsOriginFocus=focusedKey;}
        // Keep the panel return route while visiting children of All Settings.
        if(destination==HOME||!(quickReturnScreen==SETTINGS&&(destination==STARTUP||destination==DEVICE||destination==SETTINGS)))quickReturnScreen=-1;
        dismissDialog();pendingQuickPage=-1;screen=destination;pickingCollection=false;pickingSlot=-1;pickingForStartup=false;render(destination==HOME?homeFocus():rememberedFocus[destination]);
    }

    @Override public boolean onKeyDown(int code,KeyEvent event) {
        if(code==KeyEvent.KEYCODE_MENU) {
            if(childMode()){if(event.getRepeatCount()==0)openParentalSettings();return true;}
            if(event.getRepeatCount()==0) {
                View view=getCurrentFocus(); Object tag=view==null?null:view.getTag();
                if(tag instanceof Tag&&(((Tag)tag).entry!=null||((Tag)tag).slot>=0)) manage((Tag)tag);
                else if(pickingCollection)toast("请按确定键选择应用，返回键取消");
                else if(screen==HOME||screen==APPS)go(SETTINGS);
            }
            return true;
        }
        return super.onKeyDown(code,event);
    }
    @Override public boolean onKeyUp(int code,KeyEvent event) {
        if(code==KeyEvent.KEYCODE_MENU) return true;
        return super.onKeyUp(code,event);
    }
    @Override public void onBackPressed() {
        if(idleView!=null){exitScreensaver();noteInput();return;}
        if(startupTarget!=null){cancelStartup(true);return;}
        if(childMode()){if(page>0){page=0;render("app0");}else {View parent=controls.get("child_parent");if(parent!=null)parent.requestFocus();}return;}
        if(pickingCollection){pickingCollection=false;screen=COLLECTION;page=0;render(pickerOriginFocus);}
        else if(pickingForStartup){pickingForStartup=false;pickingSlot=-1;screen=STARTUP;render("startup2");}
        else if(pickingSlot>=0) {int slot=pickingSlot;pickingSlot=-1;screen=HOME;selected=slot;render("dock"+slot);}
        else if(screen==quickReturnScreen){returnToQuickSettings();}
        else if(screen==STARTUP){screen=SETTINGS;render("setting0");}
        else if(screen==DEVICE){screen=SETTINGS;render("setting5");}
        else if(screen==UPDATES){screen=updatesOrigin;page=normalAppsPage;render(updatesOriginFocus);}
        else if(isOrganizedGrid()){screen=APPS;page=collectionOriginPage;render(collectionOriginFocus);}
        else if(screen==SETTINGS){screen=settingsOrigin;page=settingsOriginPage;render(settingsOriginFocus);}
        else if(screen!=HOME) go(HOME);
        else if(!isDefaultHome()) super.onBackPressed();
    }

    private void refreshApps() {
        if(destroyed) return;
        // Coalesce repeated refreshes while PackageManager is busy: at most one rerun.
        if(scanRunning){scanAgain=true;return;}scanRunning=true;
        final int generation=++scanGeneration;
        loading=true;
        if(isAppGrid()) {
            if(!resumed)uiNeedsRefresh=true;
            else if(currentDialog!=null&&currentDialog.isShowing())refreshAfterDialog=true;
            else render(focusedKey);
        }
        worker.execute(new Runnable() {
            @Override public void run() {
                List<AppEntry> result;
                boolean failed=false;
                try {result=AppCatalog.scan(getPackageManager(),getPackageName());}
                catch(RuntimeException error) {result=new ArrayList<AppEntry>();failed=true;}
                final List<AppEntry> found=result; final boolean error=failed;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if(destroyed||generation!=scanGeneration) return;
                        loading=false;scanError=error;scanRunning=false;
                        if(error)diagnostics.event("应用扫描失败","保留已有应用、绑定与排序");
                        String anchor=focusedPackage();
                        if(!error) {apps.clear();apps.addAll(found);icons.clear();}
                        String restore=gridFocusForPackage(anchor);
                        if(!resumed)uiNeedsRefresh=true;
                        else if(screen==HOME){if(childMode()){if(currentDialog!=null)refreshAfterDialog=true;else render(restore);}else {updateHero();applyBadges();}}
                        else if(currentDialog!=null&&currentDialog.isShowing())refreshAfterDialog=true;
                        else render(restore);
                        checkStartup();
                        if(scanAgain){scanAgain=false;refreshApps();}
                    }
                });
            }
        });
    }

    private AppEntry byPackage(String pkg) {
        for(AppEntry entry:apps) if(entry.packageName.equals(pkg)) return entry;
        return null;
    }

    private Bitmap icon(AppEntry entry) {
        Bitmap cached=icons.get(entry.packageName);if(cached!=null) return cached;
        try {
            Drawable drawable=entry.info.loadIcon(getPackageManager());
            int size=Math.min(performance.iconSize,Math.max(48,u(56)));
            Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
            drawable.setBounds(0,0,size,size);drawable.draw(new Canvas(bitmap));
            icons.put(entry.packageName,bitmap);return bitmap;
        } catch(OutOfMemoryError error) {icons.clear();return null;}
          catch(RuntimeException error) {return null;}
    }

    private boolean launch(AppEntry entry) {
        if(entry==null||!childLaunchAllowed(entry.packageName))return false;
        if(!experience.beginLaunch(SystemClock.elapsedRealtime()))return true;
        showLaunchFeedback(entry.label);
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(entry.category).setComponent(entry.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        boolean opened=start(intent);
        diagnostics.event(opened?"应用打开请求":"应用打开失败",entry.packageName);
        int recorded=organizer.recordLaunch(entry.packageName,entry.component.flattenToString(),System.currentTimeMillis(),opened);
        if(recorded==AppOrganizer.WRITE_ERROR)toast("打开请求已发出，但最近记录保存失败");
        if(!opened) {
            clearLaunchFeedback();
            if(isOrganizedGrid())dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("暂时无法打开")
                    .setMessage(entry.label+" 可能已停用、卸载，或系统未允许打开。可以返回列表，刷新后重试。收藏与最近记录不会因此自动删除。")
                    .setPositiveButton("返回列表",null));
            else toast("无法打开此应用，请检查是否被停用或已卸载");
            refreshApps();
        }
        return opened;
    }
    private boolean start(Intent intent) {
        if(childMode()){
            ComponentName component=intent.getComponent();
            if(component==null||!Intent.ACTION_MAIN.equals(intent.getAction())||!childLaunchAllowed(component.getPackageName()))return false;
        }
        try {startActivity(intent);return true;}
        catch(RuntimeException error) {return false;}
    }
    private void appInfo(AppEntry entry) {
        if(!start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+entry.packageName)))) openSettings();
    }
    private void openSettings() {
        if(childMode()){openParentalSettings();return;}
        if(!start(new Intent(Settings.ACTION_SETTINGS))) toast("系统未提供可打开的设置页面");
    }
    private void openNetworkSettings() {
        if(childMode()){openParentalSettings();return;}
        if(!start(new Intent(Settings.ACTION_WIFI_SETTINGS))&&!start(new Intent(Settings.ACTION_WIRELESS_SETTINGS))) openSettings();
    }

    private boolean isDefaultHome() {
        try {
            ResolveInfo home=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
            return home!=null&&home.activityInfo!=null&&getPackageName().equals(home.activityInfo.packageName);
        } catch(RuntimeException error) {return false;}
    }

    private void defaultHome() {
        if(isDefaultHome()) {toast("栖屏已是默认桌面");return;}
        if(Build.VERSION.SDK_INT>=19&&start(new Intent("android.settings.HOME_SETTINGS"))) return;
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("设为默认桌面")
                .setMessage("按遥控器的主页键，如果出现桌面选择窗口，选择「栖屏桌面」并选择「始终」。\n\n如果仍直接回到原厂桌面，可在系统设置的「应用 / 默认应用 / 桌面」中选择栖屏；旧系统也可在原桌面的应用信息中清除默认设置。部分电视厂商不开放此选项。")
                .setPositiveButton("系统设置",new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d,int which) {openSettings();}
                }).setNegativeButton("知道了",null));
    }

    private void chooseOtherHome() {
        final List<ResolveInfo> homes=new ArrayList<ResolveInfo>();
        try {
            for(ResolveInfo info:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)) {
                if(info.activityInfo!=null&&info.activityInfo.exported&&info.activityInfo.enabled
                        &&!getPackageName().equals(info.activityInfo.packageName)) homes.add(info);
            }
        } catch(RuntimeException error) {toast("无法读取其他桌面");return;}
        if(homes.isEmpty()) {toast("没有找到其他可打开的桌面");return;}
        String[] labels=new String[homes.size()];
        for(int i=0;i<labels.length;i++) labels[i]=homes.get(i).loadLabel(getPackageManager()).toString();
        dialog(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("打开其他桌面").setItems(labels,new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d,int which) {
                ResolveInfo info=homes.get(which);
                if(!start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setComponent(new ComponentName(info.activityInfo.packageName,info.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))) toast("该桌面无法打开");
            }
        }).setNegativeButton("取消",null));
    }

    private Favorites loadFavorites() {
        List<String> list=new ArrayList<String>();
        try {JSONArray array=new JSONArray(preferences.getString("favorites","[]"));
            for(int i=0;i<array.length();i++) list.add(array.optString(i,""));
        } catch(Exception error) { /* Ignore corrupt preferences rather than failing the home screen. */ }
        return new Favorites(list);
    }
    private void saveFavorites() {preferences.edit().putString("favorites",new JSONArray(favorites.snapshot()).toString()).apply();}

    private void updateClock() {
        Date now=new Date();
        if(clockText!=null) clockText.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now));
        if(dateText!=null) dateText.setText(new SimpleDateFormat("M月d日  EEEE",Locale.SIMPLIFIED_CHINESE).format(now));
        if(networkText!=null||networkIcon!=null) {
            String status="未连接网络";
            try {
                ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo active=cm==null?null:cm.getActiveNetworkInfo();
                if(active!=null&&active.isConnected()) status=active.getType()==1?"Wi-Fi 已连接":active.getType()==9?"有线网络已连接":"网络已连接";
            } catch(RuntimeException ignored) {status="网络状态不可用";}
            if(networkText!=null)networkText.setText(status);
            if(networkIcon!=null){networkIcon.setGlyph(status.contains("已连接")?"network":"offline");networkIcon.setContentDescription("网络设置，"+status);}
        }
    }

    private boolean saveLayout(boolean saved){if(!saved){toast("保存失败或列表过大，请检查空间后重试");return false;}render(gridFocusForPackage(focusedPackage()));return true;}
    private void menu(GlassDialog.Builder builder,final Runnable parent){
        builder.setNegativeButton("返回",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(parent!=null)parent.run();}}).setBackAction(parent);dialog(builder);
    }
    private boolean childMode(){return parental!=null&&parental.enabled();}
    private void endParentSession(){parentEpoch++;parentAuthorized=false;boolean was=parentFlow;parentFlow=false;if(was)dismissDialog();}
    private void parentalReturn(){
        endParentSession();
        if(childMode())go(HOME);else showQuickSettings(QuickSettingsDialog.FAMILY,"family_minor",true);
    }
    private void openParentalSettings(){
        cancelStartup(false);parentFlow=true;parentAuthorized=false;
        if(parental.configured())askParentPassword(false);else if(childMode())parentalInfo("密码记录不可用","无法读取家长密码。为避免放开限制，本次保持未成年模式。请检查电视存储后重启栖屏重试。",true);else showParentalPanel();
    }
    private void askParentPassword(final boolean recovery){
        parentFlow=true;
        final int epoch=++parentEpoch;
        final NumberEntryDialog entry=new NumberEntryDialog(this,scene,unit,recovery?"使用恢复码":"家长验证",recovery?"输入设置密码时保存的 12 位恢复码":"请输入 6 位家长密码",recovery?12:6,true,new NumberEntryDialog.Submit(){public void submit(final NumberEntryDialog dialog,final String value){
            if(epoch!=parentEpoch)return;dialog.busy();
            parentWorker.execute(new Runnable(){public void run(){
                final int result=parental.authenticate(value,recovery,System.currentTimeMillis(),SystemClock.elapsedRealtime(),BootSession.current(MainActivity.this));
                handler.post(new Runnable(){public void run(){
                    if(destroyed||!resumed||epoch!=parentEpoch||currentDialog!=dialog)return;
                    if(result==ParentalStore.OK){parentAuthorized=true;dismissDialog();if(recovery)beginParentPin();else showParentalPanel();}
                    else dialog.error(result==ParentalStore.LOCKED?"连续输错，请稍后再试（最多 30 秒）":result==ParentalStore.WRONG?"输入不正确，请重新输入":"验证未完成，请检查存储后重试");
                }});
            }});
        }},recovery?null:new Runnable(){public void run(){askParentPassword(true);}});
        showDialog(entry,new Runnable(){public void run(){parentAuthorized=false;parentFlow=false;parentEpoch++;}});
    }
    private void beginParentPin(){
        if(parental.configured()&&!parentAuthorized){askParentPassword(false);return;}
        parentFlow=true;
        showDialog(new NumberEntryDialog(this,scene,unit,"设置家长密码","输入新的 6 位数字密码",6,true,new NumberEntryDialog.Submit(){public void submit(NumberEntryDialog dialog,final String first){
            dismissDialog();
            showDialog(new NumberEntryDialog(MainActivity.this,scene,unit,"再次输入密码","再输入一遍，确认没有输错",6,true,new NumberEntryDialog.Submit(){public void submit(final NumberEntryDialog confirm,String second){
                if(!first.equals(second)){confirm.error("两次密码不同，请重新确认");return;}
                confirm.busy();final int epoch=++parentEpoch;
                parentWorker.execute(new Runnable(){public void run(){
                    ParentalStore.Credentials prepared=null;try{prepared=ParentalStore.prepare(first);}catch(Exception ignored){}
                    final ParentalStore.Credentials result=prepared;
                    handler.post(new Runnable(){public void run(){
                        if(destroyed||!resumed||epoch!=parentEpoch||currentDialog!=confirm)return;
                        if(result==null){confirm.error("密码设置未完成，请重试");return;}
                        showRecoveryCode(result);
                    }});
                }});
            }},null),new Runnable(){public void run(){showParentalPanel();}});
        }},null),new Runnable(){public void run(){showParentalPanel();}});
    }
    private void showRecoveryCode(final ParentalStore.Credentials value){
        String code=value.recoveryCode.substring(0,4)+"  "+value.recoveryCode.substring(4,8)+"  "+value.recoveryCode.substring(8);
        Dialog shown=new GlassDialog.Builder(this,scene,unit,largeText).setTitle("请保存家长恢复码")
            .setMessage(code+"\n\n忘记密码时，可凭此码重新设置。请单独保存，不要交给孩子。本次设置将替换原密码和原恢复码。\n\n下一步需要输入恢复码完成确认。")
            .setPositiveButton("已保存，验证恢复码",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                showDialog(new NumberEntryDialog(MainActivity.this,scene,unit,"确认恢复码","输入刚保存的 12 位恢复码",12,true,new NumberEntryDialog.Submit(){public void submit(NumberEntryDialog dialog,String typed){
                    if(!value.recoveryCode.equals(typed)){dialog.error("恢复码不一致，请检查记录");return;}
                    if(!parental.saveCredentials(value)){dialog.error("保存失败，原密码保持不变");return;}
                    parentAuthorized=true;dismissDialog();showParentalPanel();toast("家长密码已保存");
                }},null),new Runnable(){public void run(){showParentalPanel();}});
            }}).setNegativeButton("取消",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){showParentalPanel();}}).setBackAction(new Runnable(){public void run(){showParentalPanel();}}).create();
        shown.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);showDialog(shown);
    }
    private void showParentalPanel(){
        if(!resumed||destroyed)return;parentFlow=true;
        if(!parental.configured()){
            menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("未成年模式")
                .setMessage("家长先设置密码和允许的应用。此模式限制栖屏入口，不能强制停止外部视频，也不能过滤应用内的内容。")
                .setItems(new String[]{"设置家长密码"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){beginParentPin();}}),new Runnable(){public void run(){parentalReturn();}});return;
        }
        if(!parentAuthorized){askParentPassword(false);return;}
        String[] items={"未成年模式："+(childMode()?"已开启":"已关闭"),"允许的应用（"+parental.allowed().size()+" 个）","允许打开的时段："+parental.window(),"修改家长密码与恢复码","功能说明"};
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("未成年模式 · 家长设置")
            .setMessage("退出或调整规则需家长验证。设置仅保存在这台电视，桌面配置备份不会覆盖这些规则。")
            .setItems(items,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                if(!parentAuthorized){askParentPassword(false);return;}
                if(which==0)confirmChildMode();else if(which==1)showAllowedApps(0);else if(which==2)showChildSchedule();else if(which==3)beginParentPin();else parentalInfo("未成年模式说明","家长选择允许的应用，并可设定每天允许从栖屏打开应用的时段。结束时间不包含在可用范围内；跨午夜时段会标注。\n\n超出时段后，栖屏显示休息页面并拒绝新的打开请求。已在外部应用播放的视频不会被强制停止。\n\n模式在待机、重启和覆盖升级后保留；开机与唤醒直播也会检查应用和时间规则。\n\n这是桌面内限制。系统快捷键、语音助手、其他桌面、应用内跳转、修改系统时间或清除栖屏数据可能绕过。请配合视频应用自身的儿童模式。",false);
            }}),new Runnable(){public void run(){parentalReturn();}});
    }
    private void parentalInfo(String title,String message,boolean close){
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(title).setMessage(message),close?new Runnable(){public void run(){parentalReturn();}}:new Runnable(){public void run(){showParentalPanel();}});
    }
    private void confirmChildMode(){
        if(!parentAuthorized)return;
        final boolean enable=!childMode();
        if(enable&&parental.allowed().isEmpty()){parentalInfo("先选择允许的应用","至少选择一个应用后才能开启。开启后，未选择的应用不会出现在儿童首页。",false);return;}
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(enable?"开启未成年模式？":"退出未成年模式？")
            .setMessage(enable?"开启后进入儿童首页，仅开放选定应用。退出与修改规则需要家长密码。\n\n可用时段："+parental.window()+"\n这是栖屏内限制，不能强制停止外部视频。":"退出后恢复原来的首页、绑定、排列和长辈布局设置。允许应用和时间规则仍会保留。")
            .setPositiveButton(enable?"开启模式":"退出模式",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                if(!parentAuthorized)return;
                if(!parental.setEnabled(enable)){parentalInfo("未能保存","请检查存储空间后重试，当前模式保持不变。",false);return;}
                cancelStartup(false);endParentSession();screen=HOME;page=0;pendingQuickPage=-1;quickReturnScreen=-1;pickingSlot=-1;pickingCollection=false;render(homeFocus());
            }}),new Runnable(){public void run(){showParentalPanel();}});
    }
    private void showAllowedApps(final int preferred){
        if(!parentAuthorized)return;
        if(loading||scanError){parentalInfo("应用列表尚未就绪","请返回刷新应用，读取完成后再设置允许的应用。已有规则保持不变。",false);return;}
        final List<String> packages=new ArrayList<String>();final List<String> labels=new ArrayList<String>();
        for(AppEntry app:apps){packages.add(app.packageName);labels.add((parental.contains(app.packageName)?"✓  ":"○  ")+app.label);}
        for(String pkg:parental.allowed())if(!packages.contains(pkg)){packages.add(pkg);labels.add("✓  "+pkg+" · 暂不可用");}
        if(packages.isEmpty()){parentalInfo("没有可选择的应用","请先安装可打开的应用，再回来选择。",false);return;}
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("允许的应用")
            .setMessage("方向键选择，确定切换允许状态。已选 "+parental.allowed().size()+" 个；仅限制从栖屏打开。")
            .setInitialItem(clamp(preferred,0,packages.size()-1)).setItems(labels.toArray(new String[0]),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                if(!parentAuthorized)return;List<String> chosen=parental.allowed();String pkg=packages.get(which);if(chosen.contains(pkg))chosen.remove(pkg);else chosen.add(pkg);
                if(!parental.setAllowed(chosen))toast("保存失败或允许应用已达上限");
                render(focusedKey);showAllowedApps(which);
            }}),new Runnable(){public void run(){showParentalPanel();}});
    }
    private void showChildSchedule(){
        if(!parentAuthorized)return;
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("允许打开的时段")
            .setMessage(!parental.validSchedule()?"时间设置异常，请重新设置开始和结束时间。开启时段限制时，修复前暂停从栖屏打开应用。":"每天按电视当前时间执行。到时停止新的打开请求，外部软件中已播放的视频不会被强制停止。")
            .setItems(new String[]{"时段限制："+(parental.scheduled()?"开启":"关闭"),"开始时间："+ParentalStore.time(parental.start()),"结束时间："+ParentalStore.time(parental.end())},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                if(!parentAuthorized)return;
                if(which==0){if(!parental.setSchedule(!parental.scheduled()))toast("时间设置保存失败");render(focusedKey);showChildSchedule();}
                else editChildTime(which==1);
            }}),new Runnable(){public void run(){showParentalPanel();}});
    }
    private void editChildTime(final boolean start){
        if(!parentAuthorized)return;
        if(!parental.validSchedule()){repairChildWindow();return;}
        showDialog(new NumberEntryDialog(this,scene,unit,start?"开始时间":"结束时间","按 HHMM 输入，例如 1930 表示 19:30",4,false,new NumberEntryDialog.Submit(){public void submit(NumberEntryDialog dialog,String value){
            if(!parentAuthorized)return;int time=ParentalStore.parseTime(value);
            if(time<0){dialog.error("请输入有效时间：00:00—23:59");return;}
            int from=start?time:parental.start(),to=start?parental.end():time;
            if(from==to){dialog.error("开始与结束不能相同；全天可关闭时段限制");return;}
            if(!parental.setWindow(from,to)){dialog.error("时间保存失败，请重试");return;}
            dismissDialog();render(focusedKey);showChildSchedule();
        }},null),new Runnable(){public void run(){showChildSchedule();}});
    }
    private void repairChildWindow(){
        if(!parentAuthorized)return;
        showDialog(new NumberEntryDialog(this,scene,unit,"重新设置开始时间","按 HHMM 输入；下一步设置结束时间",4,false,new NumberEntryDialog.Submit(){public void submit(NumberEntryDialog dialog,String value){
            if(!parentAuthorized)return;final int from=ParentalStore.parseTime(value);
            if(from<0){dialog.error("请输入有效时间：00:00—23:59");return;}
            showDialog(new NumberEntryDialog(MainActivity.this,scene,unit,"重新设置结束时间","开始时间："+ParentalStore.time(from)+"；确定后保存完整时段",4,false,new NumberEntryDialog.Submit(){public void submit(NumberEntryDialog end,String value){
                if(!parentAuthorized)return;int to=ParentalStore.parseTime(value);
                if(to<0){end.error("请输入有效时间：00:00—23:59");return;}
                if(from==to){end.error("开始与结束不能相同；全天可关闭时段限制");return;}
                if(!parental.setWindow(from,to)){end.error("时间保存失败，请重试");return;}
                dismissDialog();render(focusedKey);showChildSchedule();
            }},null),new Runnable(){public void run(){repairChildWindow();}});
        }},null),new Runnable(){public void run(){showChildSchedule();}});
    }
    private void buildChildHome(){
        StageLayout layout=new StageLayout(this);layout.setPageSwipe(new StageLayout.PageSwipe(){public void onPageSwipe(boolean right){swipePage(right);}});
        surface.addView(layout,new FrameLayout.LayoutParams(-1,-1));activeLayer=layout;
        int margin=DisplayPreferences.margin(preferences);float edge=new float[]{30,48,66}[margin],top=margin==2?36:24;
        layout.place(text("栖屏",16,TEXT,false),edge+12,top+3,110,28);
        clockText=text("00:00",16,TEXT,false);clockText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);layout.place(clockText,960-edge-214,top+3,64,28);
        GlassButton parent=toolbarText("child_parent","家长管理",new Runnable(){public void run(){openParentalSettings();}});layout.place(parent,960-edge-136,top-4,136,44);
        List<View> header=new ArrayList<View>();header.add(parent);focusRows.add(header);
        childTimeOpen=parental.timeAllowed(System.currentTimeMillis());
        heroTitle=text(childTimeOpen?"未成年模式":"现在是休息时间",largeText?40:36,TEXT,true);layout.place(heroTitle,edge+10,top+51,730,55);
        heroSubtitle=text(childTimeOpen?"选择家长允许的应用":"可打开应用的时段："+parental.window(),largeText?16:14,GlassPanel.SECONDARY,false);heroSubtitle.setSingleLine();layout.place(heroSubtitle,edge+12,top+107,960-2*edge-24,26);
        gridApps.clear();if(childTimeOpen)gridApps.addAll(visibleGrid());
        int pages=Math.max(1,(gridApps.size()+PAGE_SIZE-1)/PAGE_SIZE);page=clamp(page,0,pages-1);float gridY=top+144,slotWidth=(960-2*edge)/6;
        if(gridApps.isEmpty()){
            GlassPanel panel=new GlassPanel(this,scene);layout.place(panel,edge+70,gridY+20,960-2*edge-140,215);
            TextView caption=text(!childTimeOpen?"休息一下，稍后再看":loading?"正在读取应用…":scanError?"暂时无法读取应用":"还没有可用的允许应用",largeText?25:23,TEXT,false);caption.setGravity(Gravity.CENTER);layout.place(caption,edge+90,gridY+66,960-2*edge-180,55);
            TextView note=text(!childTimeOpen?"家长可以输入密码调整规则":"请家长检查允许列表，或返回首页重新读取",largeText?17:15,GlassPanel.SECONDARY,false);note.setGravity(Gravity.CENTER);layout.place(note,edge+90,gridY+130,960-2*edge-180,50);
        }else for(int r=0;r<2;r++){
            List<View> row=new ArrayList<View>();
            for(int c=0;c<6;c++){final int index=page*PAGE_SIZE+r*6+c;if(index>=gridApps.size())break;final AppEntry app=gridApps.get(index);
                AppTileView tile=libraryTile("app"+index,app,new Runnable(){public void run(){launch(app);}});tile.setOnLongClickListener(null);tile.setLongClickable(false);tile.setUpdateAvailable(false);
                layout.place(tile,edge+c*slotWidth,gridY+r*135,slotWidth,130);row.add(tile);
            }if(!row.isEmpty())focusRows.add(row);
        }
        if(page>0)layout.place(new PageEdgeView(this,scene,false),edge-22,gridY+100,16,42);
        if(page<pages-1)layout.place(new PageEdgeView(this,scene,true),960-edge+6,gridY+100,16,42);
        layout.place(text("方向键选择 · 确定打开 · 家长管理需密码",largeText?14:12,GlassPanel.SECONDARY,false),edge+12,margin==2?478:482,800,28);
    }
    private void updateChildHome(){
        if(!childMode()||screen!=HOME)return;
        boolean allowed=parental.timeAllowed(System.currentTimeMillis());
        if(allowed!=childTimeOpen){if(currentDialog!=null)refreshAfterDialog=true;else render(focusedKey);return;}
        if(heroSubtitle!=null){if(startupTarget!=null)heroSubtitle.setText(Math.max(1,(startupDeadline-SystemClock.elapsedRealtime()+999)/1000)+" 秒后打开 "+startupTarget.label+" · 按返回取消");else heroSubtitle.setText(allowed?"选择家长允许的应用":"可打开应用的时段："+parental.window());}
    }
    private boolean childLaunchAllowed(String pkg){
        if(parental.mayLaunch(pkg,System.currentTimeMillis()))return true;
        toast(parental.contains(pkg)?"当前为休息时段，请家长调整规则":"此应用尚未获得家长允许");return false;
    }

    private void showPersonalization(){
        if(wallpaperBusy){toast("壁纸正在处理，请稍候");return;}
        showQuickSettings(QuickSettingsDialog.PERSONAL,null,true);
    }
    private void showHomeLayout(){
        final int[] order=workspace.home(false);String[] labels=new String[6];
        for(int i=0;i<5;i++)labels[i]=(i+1)+". "+ShortcutStore.NAMES[order[i]]+((workspace.hiddenHome()&(1<<order[i]))!=0?" · 已隐藏":order[i]==4?" · 保留入口":" · 显示");labels[5]="恢复默认首页";
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("首页排列与隐藏").setMessage("设置与全部应用入口始终保留。此处调整标准首页，长辈布局保持三个大入口。")
            .setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==5){if(layoutEditable())saveLayout(workspace.resetHome());showHomeLayout();}else showHomeItem(order[which]);}}),new Runnable(){public void run(){showPersonalization();}});
    }
    private void showHomeItem(final int slot){
        final boolean hidden=(workspace.hiddenHome()&(1<<slot))!=0;int[] order=workspace.home(false);int position=0;for(int i=0;i<order.length;i++)if(order[i]==slot)position=i;
        final List<Integer> actions=new ArrayList<Integer>();List<String> labels=new ArrayList<String>();
        if(position>0){labels.add("向左移动");actions.add(-1);}if(position<4){labels.add("向右移动");actions.add(1);}if(slot<4){labels.add(hidden?"显示此入口":"隐藏此入口");actions.add(2);}
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(ShortcutStore.NAMES[slot]+" · 首页入口").setMessage("仅调整显示位置，原来的应用绑定会保留。")
            .setItems(labels.toArray(new String[0]),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(layoutEditable()){int action=actions.get(which);saveLayout(action==2?workspace.hideHome(slot,!hidden):workspace.moveHome(slot,action));}showHomeItem(slot);}}),new Runnable(){public void run(){showHomeLayout();}});
    }
    private void showAppManager(){
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("整理应用").setMessage("在应用上长按确定或按菜单键，可移动或隐藏。隐藏仅影响全部应用列表，不影响首页绑定与收藏。")
            .setItems(new String[]{"已隐藏应用（"+workspace.hiddenApps().size()+"）","恢复默认排序","显示全部隐藏应用"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                if(which==0)showHiddenApps();else if(layoutEditable()){saveLayout(which==1?workspace.resetAppOrder():workspace.showAll());showAppManager();}
            }}),screen==SETTINGS?new Runnable(){public void run(){showPersonalization();}}:null);
    }
    private void showHiddenApps(){
        final List<String> hidden=workspace.hiddenApps();String[] labels=new String[hidden.size()];for(int i=0;i<labels.length;i++){AppEntry app=byPackage(hidden.get(i));labels[i]=(app==null?hidden.get(i)+" · 暂不可用":app.label)+" · 恢复显示";}
        GlassDialog.Builder builder=new GlassDialog.Builder(this,scene,unit,largeText).setTitle("已隐藏应用").setMessage(hidden.isEmpty()?"当前没有隐藏应用。":"选择应用后按确定，即可恢复显示。");
        if(!hidden.isEmpty())builder.setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(layoutEditable())saveLayout(workspace.hideApp(hidden.get(which),false));showHiddenApps();}});
        menu(builder,new Runnable(){public void run(){showAppManager();}});
    }
    private void showAppLayout(final AppEntry entry){
        if(!layoutEditable())return;
        final boolean hidden=workspace.hidden(entry.packageName);final List<Integer> actions=new ArrayList<Integer>();List<String> labels=new ArrayList<String>();
        List<AppEntry> ordered=workspace.visible(apps);int position=-1;for(int i=0;i<ordered.size();i++)if(entry.packageName.equals(ordered.get(i).packageName)){position=i;break;}
        if(position>0){labels.add("移到最前");actions.add(0);labels.add("向前移动一格");actions.add(-1);}if(position>=0&&position+1<ordered.size()){labels.add("向后移动一格");actions.add(1);}
        labels.add(hidden?"恢复显示":"在全部应用中隐藏");actions.add(2);
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(entry.label+" · 排列与隐藏").setMessage("移动后焦点跟随应用。隐藏不会卸载应用，也不会删除收藏或绑定。")
            .setItems(labels.toArray(new String[0]),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(!layoutEditable())return;int action=actions.get(which);boolean saved=action==2?workspace.hideApp(entry.packageName,!hidden):workspace.moveApp(entry.packageName,action,apps);
                if(saved){String focus=gridFocusForPackage(entry.packageName);render(focus);if(action!=2)showAppLayout(entry);else toast(hidden?"已恢复显示":"已隐藏，可在「整理应用」中恢复");}else toast("保存失败或列表过大，请稍后重试");
            }}),null);
    }
    private void showScreensaverSettings(){
        final int[] values={0,5,10,15,30};int current=0;for(int i=0;i<values.length;i++)if(values[i]==workspace.saverMinutes())current=i;
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("桌面空闲屏保").setMessage("仅在首页空闲时显示当前壁纸与时钟，进入直播或其他应用后停止计时。系统休眠设置仍然有效。")
            .setSingleChoiceItems(new String[]{"关闭","5 分钟后","10 分钟后","15 分钟后","30 分钟后","立即预览"},current,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                if(which==5){go(HOME);handler.postDelayed(new Runnable(){public void run(){enterScreensaver();}},200);}
                else {if(!workspace.setSaver(values[which]))toast("屏保设置保存失败");noteInput();showScreensaverSettings();}
            }}),new Runnable(){public void run(){showPersonalization();}});
    }
    private static String wallpaperName(int category){return category==5?"设置页面":ShortcutStore.NAMES[category];}
    private void showWallpapers(){
        String[] labels=new String[7];for(int i=0;i<6;i++)labels[i]=wallpaperName(i)+(wallpapers.has(i)?" · 本地图片":" · 内置壁纸");labels[6]="手机传壁纸";
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("本地壁纸").setMessage("为每个界面单独选图，也可用手机扫码传图。图片保存后可离线使用。")
            .setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==6)showPhoneWallpaper(selected);else showWallpaperActions(which);}}),new Runnable(){public void run(){showPersonalization();}});
    }
    private void showWallpaperActions(final int category){
        menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle(wallpaperName(category)+" · 壁纸").setMessage("建议横向图片。单张不超过 20 MB，保存时自动缩小；玻璃图标随壁纸显色。")
            .setItems(new String[]{"选择本地图片","恢复内置壁纸","手机传壁纸"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==0)pickWallpaper(category);else if(which==2)showPhoneWallpaper(category);else {if(wallpapers.reset(category)){scene.reloadWallpapers();toast("已恢复内置壁纸");}else toast("壁纸恢复失败，请稍后重试");showWallpaperActions(category);}}}),new Runnable(){public void run(){showWallpapers();}});
    }
    private void showPhoneWallpaper(final int category){
        if(wallpaperBusy){toast("正在处理上一张图片，请稍候");return;}
        final int slot=clamp(category,0,5);
        showDialog(new PhoneWallpaperDialog(this,scene,wallpapers,unit,slot,new Runnable(){public void run(){scene.reloadWallpapers();}},new Runnable(){public void run(){if(resumed&&!destroyed)showPhoneWallpaper(slot);}}),new Runnable(){public void run(){if(resumed&&!destroyed)showWallpaperActions(slot);}});
    }
    private void pickWallpaper(int category){
        if(wallpaperBusy){toast("正在处理上一张图片");return;}
        wallpaperScene=category;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(intent,WALLPAPER_IMPORT);}catch(RuntimeException missing){
            try{intent.setAction(Intent.ACTION_GET_CONTENT);startActivityForResult(intent,WALLPAPER_IMPORT);}catch(RuntimeException error){wallpaperScene=-1;menu(new GlassDialog.Builder(this,scene,unit,largeText).setTitle("没有可用的图片选择器").setMessage("电视系统未提供文件选择界面。安装支持选择图片的文件管理器后再试，或先在手机、模拟器中体验。"),new Runnable(){public void run(){showWallpapers();}});}
        }
    }
    private void importWallpaper(final int category,final Uri uri){
        if(wallpaperBusy)return;wallpaperBusy=true;wallpaperCancelled=false;
        final Dialog progress=new GlassDialog.Builder(this,scene,unit,largeText).setTitle("正在保存壁纸").setMessage("图片处理完成后会自动应用。原壁纸会保留到新图片保存成功。")
            .setNegativeButton("取消",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){wallpaperCancelled=true;}}).create();
        progress.setCanceledOnTouchOutside(false);progress.setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface d){wallpaperCancelled=true;}});showDialog(progress);
        wallpaperWorker.execute(new Runnable(){public void run(){String failure=null;try{wallpapers.importUri(category,uri,new WallpaperStore.Cancellation(){public boolean cancelled(){return wallpaperCancelled||destroyed;}});}catch(Exception error){failure=error.getMessage();}
            final String message=failure;handler.post(new Runnable(){public void run(){if(destroyed)return;wallpaperBusy=false;if(currentDialog==progress)dismissDialog();if(message==null){scene.reloadWallpapers();toast("壁纸已保存，拔掉 U 盘后仍可使用");}else if(!wallpaperCancelled)toast(message==null?"壁纸导入失败":message);
                if(resumed&&currentDialog==null)showWallpaperActions(category);noteInput();
            }});
        }});
    }
    private void showLaunchFeedback(String label){
        removeLaunchFeedbackView();
        TextView caption=text("正在打开 "+label+"…",18,TEXT,false);caption.setSingleLine();caption.setEllipsize(TextUtils.TruncateAt.END);caption.setGravity(Gravity.CENTER);caption.setPadding(u(20),0,u(20),0);
        FrameLayout panel=new FrameLayout(this);launchPanel=new GlassPanel(this,scene);panel.addView(launchPanel,new FrameLayout.LayoutParams(-1,-1));panel.addView(caption,new FrameLayout.LayoutParams(-1,-1));panel.setFocusable(false);panel.setContentDescription("正在打开 "+label);launchFeedback=panel;
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(u(430),u(54),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);params.bottomMargin=u(100);surface.addView(panel,params);
        handler.removeCallbacks(launchTimeout);handler.postDelayed(launchTimeout,ExperiencePolicy.LAUNCH_WINDOW_MS);handler.removeCallbacks(idleCheck);
    }
    private void removeLaunchFeedbackView(){if(launchPanel!=null&&scene!=null)scene.removeGlass(launchPanel);launchPanel=null;if(launchFeedback!=null&&surface!=null)surface.removeView(launchFeedback);launchFeedback=null;}
    private void clearLaunchFeedback(){handler.removeCallbacks(launchTimeout);experience.clearLaunch();removeLaunchFeedbackView();}
    private void noteInput(){lastInput=SystemClock.elapsedRealtime();scheduleIdle();}
    private boolean idleEligible(){return workspace!=null&&!destroyed&&ExperiencePolicy.canIdle(resumed,hasWindowFocus(),screen==HOME,currentDialog!=null,startupTarget!=null,configApplying||wallpaperBusy||experience.launching(SystemClock.elapsedRealtime()));}
    private void scheduleIdle(){handler.removeCallbacks(idleCheck);if(idleView==null&&idleEligible()&&workspace.saverMinutes()>0)handler.postDelayed(idleCheck,ExperiencePolicy.remaining(SystemClock.elapsedRealtime(),lastInput,workspace.saverMinutes()));}
    private void enterScreensaver(){if(!idleEligible()||idleView!=null)return;cancelConfirm();handler.removeCallbacks(idleCheck);scene.finishAnimation();idleReturnFocus=focusedKey;activeLayer.setVisibility(View.INVISIBLE);idleView=new IdleClockView(this,scene);surface.addView(idleView,new FrameLayout.LayoutParams(-1,-1));idleClock.run();}
    private void exitScreensaver(){handler.removeCallbacks(idleClock);if(idleView!=null){String returnFocus=idleReturnFocus;idleReturnFocus=null;if(surface!=null)surface.removeView(idleView);idleView=null;if(activeLayer!=null)activeLayer.setVisibility(View.VISIBLE);View target=controls.get(returnFocus);if(!destroyed&&target!=null)target.requestFocus();}}
    private static boolean isWakeKey(int code){return (code>=KeyEvent.KEYCODE_DPAD_UP&&code<=KeyEvent.KEYCODE_DPAD_CENTER)||code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_MENU||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A;}

    private void dialog(GlassDialog.Builder builder) {
        showDialog(builder.create());
    }
    private void showDialog(final Dialog shown){showDialog(shown,null);}
    private void showDialog(final Dialog shown,final Runnable afterDismiss) {
        exitScreensaver();handler.removeCallbacks(idleCheck);dismissDialog();currentDialog=shown;
        shown.setOnDismissListener(new DialogInterface.OnDismissListener(){@Override public void onDismiss(DialogInterface dialog){
            if(currentDialog!=shown)return;currentDialog=null;if(destroyed)return;
            clearMenuTarget();
            if(refreshAfterDialog){refreshAfterDialog=false;render(gridFocusForPackage(focusedPackage()));}
            else {View view=controls.get(focusedKey);if(view!=null)view.requestFocus();}
            noteInput();if(afterDismiss!=null)afterDismiss.run();
        }});
        shown.show();
    }
    private void dismissDialog() {
        if(currentDialog!=null){Dialog old=currentDialog;currentDialog=null;old.dismiss();}
        clearMenuTarget();
    }
    private void clearMenuTarget(){for(View view:controls.values())if(view instanceof AppTileView)((AppTileView)view).setMenuTarget(false);}
    private void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private TextView text(String value,int size,int color,boolean bold) {
        TextView text=new TextView(this);text.setText(value);text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX,u(size)*DisplayPreferences.extraScale(fontMode));
        text.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        text.setIncludeFontPadding(false);text.setGravity(Gravity.CENTER_VERTICAL);
        text.setFocusable(false);return text;
    }
    private static int clamp(int value,int min,int max) {return Math.max(min,Math.min(max,value));}
}
