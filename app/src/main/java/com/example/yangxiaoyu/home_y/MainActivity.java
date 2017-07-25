package com.example.yangxiaoyu.home_y;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.IActivityManager;
import android.app.ProfilerInfo;
import android.app.SearchManager;
import android.app.UiAutomationConnection;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ShellCommand;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.R.attr.name;
import static java.lang.Thread.sleep;


public class MainActivity extends Activity {
    public ProfilerInfo profilerInfo = null;
     public ActivityOptions options = null;
     public IActivityManager.WaitResult result = null;
    public static final String FATAL_ERROR_CODE = "Error type 1";
    public static final String NO_SYSTEM_ERROR_CODE = "Error type 2";
    public static final String NO_CLASS_ERROR_CODE = "Error type 3";
    public ApplicationInfo app;

    private int mStartFlags = 0;
    private boolean mWaitOption = false;
    private boolean mStopOption = false;
    public static final int INVALID_STACK_ID = -1;
    private int mRepeat = 0;
    private int mUserId;
    private String mReceiverPermission;

    private String mProfileFile;
    private int mSamplingInterval;
    private boolean mAutoStop;
    private int mStackId;
    private IPackageManager mPm;
    private IActivityManager mAm;
    ComponentName cn;
    String profileFile = null;
    Bundle args = new Bundle();
    UiAutomationConnection connection = null;
    String abi = null;
    int userId = 0;
    public ResolveInfo info = null;
    private static final String LOG_TAG = "Home";

    private static final String KEY_SAVE_GRID_OPENED = "grid.opened";

    // Identifiers for option menu items
    private static final int MENU_WALLPAPER_SETTINGS = Menu.FIRST + 1;
    private static final int MENU_SEARCH = MENU_WALLPAPER_SETTINGS + 1;
    private static final int MENU_SETTINGS = MENU_SEARCH + 1;

    private static final int MAX_RECENT_TASKS = 20;
    private static final String TAG ="MainActivity" ;

    private static boolean mWallpaperChecked;
    private static ArrayList<ApplicationInfo> mApplications;
    private final BroadcastReceiver mWallpaperReceiver = new WallpaperIntentReceiver();
    private final BroadcastReceiver mApplicationsReceiver = new ApplicationsIntentReceiver();

    private GridView mGrid;

    private LayoutAnimationController mShowLayoutAnimation;
    private LayoutAnimationController mHideLayoutAnimation;

    private boolean mBlockAnimation;

    private boolean mHomeDown;
    private boolean mBackDown;

    private View mShowApplications;
    private CheckBox mShowApplicationsCheck;

    private ApplicationsStackLayout mApplicationsStack;
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        setContentView(R.layout.home);

        registerIntentReceivers();

        setDefaultWallpaper();

        loadApplications(true);

        bindApplications();
        bindButtons();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Close the menu
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            getWindow().closeAllPanels();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove the callback for the cached drawables or we leak
        // the previous Home screen on orientation change
        final int count = mApplications.size();
        for (int i = 0; i < count; i++) {
            mApplications.get(i).icon.setCallback(null);
        }

        unregisterReceiver(mWallpaperReceiver);
        unregisterReceiver(mApplicationsReceiver);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        final boolean opened = state.getBoolean(KEY_SAVE_GRID_OPENED, false);
        if (opened) {
            showApplications(false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_SAVE_GRID_OPENED, mGrid.getVisibility() == View.VISIBLE);
    }

    /**
     * Registers various intent receivers. The current implementation registers
     * only a wallpaper intent receiver to let other applications change the
     * wallpaper.
     *///IntentReceiver 再开启某个应用
    private void registerIntentReceivers() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
        registerReceiver(mWallpaperReceiver, filter);

        filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(mApplicationsReceiver, filter);
    }

    /**
     * Creates a new appplications adapter for the grid view and registers it.
     */
    private void bindApplications() {
        if (mGrid == null) {
            mGrid = (GridView) findViewById(R.id.all_apps);
        }
        mGrid.setAdapter(new ApplicationsAdapter(this, mApplications));
    }

    /**
     * Binds actions to the various buttons.
     */
    private void bindButtons() {
        new ShowApplications();
        mGrid.setOnItemClickListener(new ApplicationLauncher());
    }
    final protected ShellCommand mArgs = new ShellCommand() {
        @Override
        public int onCommand(String cmd) {
            return 0;
        }

        @Override
        public void onHelp() {
        }
    };

    /**
     * When no wallpaper was manually set, a default wallpaper is used instead.
     */
    private void setDefaultWallpaper() {
        if (!mWallpaperChecked) {
            Drawable wallpaper = peekWallpaper();
            if (wallpaper == null) {
                try {
                    clearWallpaper();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to clear wallpaper " + e);
                }
            } else {
                getWindow().setBackgroundDrawable(new ClippedDrawable(wallpaper));
            }
            mWallpaperChecked = true;
        }
    }


    private static void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {

        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
            // Empty
        }

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                    ", expected " + firstElementName);
        }
    }

    private static void nextElement(XmlPullParser parser) throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
        }
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            mBackDown = mHomeDown = false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    mBackDown = true;
                    return true;
                case KeyEvent.KEYCODE_HOME:
                    mHomeDown = true;
                    return true;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    if (!event.isCanceled()) {
                        // Do BACK behavior.
                    }
                    mBackDown = true;
                    return true;
                case KeyEvent.KEYCODE_HOME:
                    if (!event.isCanceled()) {
                        // Do HOME behavior.
                    }
                    mHomeDown = true;
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_WALLPAPER_SETTINGS, 0, R.string.menu_wallpaper)
                .setIcon(android.R.drawable.ic_menu_gallery)
                .setAlphabeticShortcut('W');
        menu.add(0, MENU_SEARCH, 0, R.string.menu_search)
                .setIcon(android.R.drawable.ic_search_category_default)
                .setAlphabeticShortcut(SearchManager.MENU_KEY);
        menu.add(0, MENU_SETTINGS, 0, R.string.menu_settings)
                .setIcon(android.R.drawable.ic_menu_preferences)
                .setIntent(new Intent(android.provider.Settings.ACTION_SETTINGS));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_WALLPAPER_SETTINGS:
                startWallpaper();
                return true;
            case MENU_SEARCH:
                onSearchRequested();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startWallpaper() {
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(pickWallpaper, getString(R.string.menu_wallpaper)));
    }

    //加载应用
    private void loadApplications(boolean isLaunching) {
        if (isLaunching && mApplications != null) {
            return;
        }
        PackageManager manager = getPackageManager();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        final List<ResolveInfo> apps = manager.queryIntentActivities(mainIntent,0);

        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(manager));//对应用进行排序
        Log.d(TAG, "loadApplications: Apps="+apps);


        if (apps != null) {
            final int count = apps.size();

            if (mApplications == null) {
                mApplications = new ArrayList<ApplicationInfo>(count);
            }
            mApplications.clear();

            for (int i = 0; i<count; i++) {
                ApplicationInfo application = new ApplicationInfo();
                info = apps.get(i);
                Log.d(TAG, "loadApplications: 应用信息"+info);

                application.title = info.loadLabel(manager);
                Log.d(TAG, "loadApplications: "+application.title.toString());
                application.setActivity(new ComponentName(
                                info.activityInfo.applicationInfo.packageName,
                                info.activityInfo.name),
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                Drawable drawable = info.activityInfo.loadIcon(manager);
                BitmapDrawable bitmapDrawable = (BitmapDrawable)drawable;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                Log.d(TAG, "loadApplications: "+width);
                Log.d(TAG, "loadApplications: "+height);
//                    int newWidth = width * 2;
//                    int newHeight = height * 2;
                Matrix matrix = new Matrix();
                matrix.postScale(1.5f,1.5f);
                Bitmap newbitmap = Bitmap.createBitmap(bitmap,0,0,width,height,matrix,true);
                Log.d(TAG, "loadApplications: "+newbitmap.getWidth());


//                    application.icon = info.activityInfo.loadIcon(manager);
                Drawable drawable1 = new BitmapDrawable(newbitmap);
                Log.d(TAG, "loadApplications: "+drawable1.getIntrinsicWidth());
                application.icon = drawable1;
                Log.d(TAG, "loadApplications: "+application.icon);
                mApplications.add(application);
            }
        }
    }

    /**
     * Shows all of the applications by playing an animation on the grid.
     */
    private void showApplications(boolean animate) {
        mShowLayoutAnimation = AnimationUtils.loadLayoutAnimation(
                this, R.anim.show_applications);
        mGrid.setVisibility(View.VISIBLE);

        if (!animate) {
            mBlockAnimation = false;
        }
    }

    /**
     * Hides all of the applications by playing an animation on the grid.
     */
    private void hideApplications() {
        if (mBlockAnimation) {
            return;
        }
        mBlockAnimation = true;

        mShowApplicationsCheck.toggle();

        if (mHideLayoutAnimation == null) {
            mHideLayoutAnimation = AnimationUtils.loadLayoutAnimation(
                    this, R.anim.hide_applications);
        }
        mGrid.setVisibility(View.INVISIBLE);
        mShowApplications.requestFocus();
    }

    /**
     * Receives intents from other applications to change the wallpaper.
     */
    private class WallpaperIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            getWindow().setBackgroundDrawable(new ClippedDrawable(getWallpaper()));
        }
    }

    /**
     * Receives notifications when applications are added/removed.
     */
    private class ApplicationsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApplications(false);
            bindApplications();
//                bindRecents();
//                bindFavorites(false);
        }
    }

    /**
     * GridView adapter to show the list of all installed applications.
     */
    private class ApplicationsAdapter extends ArrayAdapter<ApplicationInfo> {
        private Rect mOldBounds = new Rect();

        public ApplicationsAdapter(Context context, ArrayList<ApplicationInfo> apps) {
            super(context,0, apps);
//            Log.d(TAG, "ApplicationsAdapter: "+apps);
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ApplicationInfo info = mApplications.get(position);

            if (convertView == null) {
                final LayoutInflater inflater = getLayoutInflater();
                convertView = inflater.inflate(R.layout.application, parent, false);
            }

            Drawable icon = info.icon;

            if (!info.filtered) {
                //final Resources resources = getContext().getResources();
                int width = 60;//(int) resources.getDimension(android.R.dimen.app_icon_size);
                int height = 60;//(int) resources.getDimension(android.R.dimen.app_icon_size);

                final int iconWidth = icon.getIntrinsicWidth();
                final int iconHeight = icon.getIntrinsicHeight();

                if (icon instanceof PaintDrawable) {
                    PaintDrawable painter = (PaintDrawable) icon;
                    painter.setIntrinsicWidth(width);
                    painter.setIntrinsicHeight(height);
                }

                if (width > 0 && height > 0 && (width < iconWidth || height < iconHeight)) {
                    final float ratio = (float) iconWidth / iconHeight;

                    if (iconWidth > iconHeight) {
                        height = (int) (width / ratio);
                    } else if (iconHeight > iconWidth) {
                        width = (int) (height * ratio);
                    }

                    final Bitmap.Config c =
                            icon.getOpacity() != PixelFormat.OPAQUE ?
                                    Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
                    final Bitmap thumb = Bitmap.createBitmap(width, height, c);
                    final Canvas canvas = new Canvas(thumb);
                    canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG, 0));
                    // Copy the old bounds to restore them later
                    // If we were to do oldBounds = icon.getBounds(),
                    // the call to setBounds() that follows would
                    // change the same instance and we would lose the
                    // old bounds
                    mOldBounds.set(icon.getBounds());
                    icon.setBounds(0, 0, width, height);
                    icon.draw(canvas);
                    icon.setBounds(mOldBounds);
                    icon = info.icon = new BitmapDrawable(thumb);
                    info.filtered = true;
                }
            }

            final TextView textView = (TextView) convertView.findViewById(R.id.label);
            textView.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
            textView.setText(info.title);

            return convertView;
        }
    }

    /**
     * Shows and hides the applications grid view.
     */
    private class ShowApplications implements View.OnClickListener {
        public void onClick(View v) {
            if (mGrid.getVisibility() != View.VISIBLE) {
                showApplications(true);
            } else {
                hideApplications();

            }
        }
    }

    /**
     * Hides the applications grid when the layout animation is over.
     */
    private class HideGrid implements Animation.AnimationListener {
        public void onAnimationStart(Animation animation) {
        }

        public void onAnimationEnd(Animation animation) {
            mBlockAnimation = false;
        }

        public void onAnimationRepeat(Animation animation) {
        }
    }

    /**
     * Shows the applications grid when the layout animation is over.
     */
    private class ShowGrid implements Animation.AnimationListener {
        public void onAnimationStart(Animation animation) {
        }

        public void onAnimationEnd(Animation animation) {
            mBlockAnimation = false;
            // ViewDebug.stopHierarchyTracing();
        }

        public void onAnimationRepeat(Animation animation) {
        }
    }

    /**
     * Starts the selected activity/application in the grid view.
     */
    private class ApplicationLauncher implements AdapterView.OnItemClickListener {

        public void onItemClick(AdapterView parent, View v, int position, long id) {
            final ApplicationInfo app = (ApplicationInfo) parent.getItemAtPosition(position);
            Log.d(TAG, "onItemClick: " + app.intent);
            Log.d(TAG, "onItemClick: " + app.intent.getType());
            Log.d(TAG, "onItemClick: " + app.intent.getData());

            //startActivity(app.intent);

            for (int i = 0; i <= 30; i++) {
                try {
                    runStart(app.intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    //------------restartPackage不行--------------------
//                    ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//                    ComponentName com = app.intent.getComponent();
//                    String name = com.getPackageName();
//                    activityManager.restartPackage(name);
                    //--------------killBackgroundProcess不行--------------------
//                    ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//                    ComponentName com = app.intent.getComponent();
//                    String name = com.getPackageName();
//                    activityManager.killBackgroundProcesses(name);
                    //---------------------------------
                    runkillApplication(app.intent);
                    //--------------------杀死的是父进程----------
//                    android.os.Process.killProcess(android.os.Process.myPid());
                    //------------------------------
//                    System.exit(0);
                    //-------------------------------
//                    MainActivity.this.finish();
                    //------------------force-stop--------
//                    runForceStop(app.intent);

                    Log.d(TAG, "onItemClick: 杀死进程已经进行"+name);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
    }
    private void runForceStop(Intent intent) throws Exception {
        mAm = ActivityManagerNative.getDefault();
        int userId = UserHandle.USER_ALL;
        Log.d(TAG, "runForceStop: 强制停止");
        ComponentName com = intent.getComponent();
        String name = com.getPackageName();
        mAm.forceStopPackage(name, userId);
    }
    private void runkillApplication(Intent intent) throws Exception {
        mAm = ActivityManagerNative.getDefault();
        int userId = UserHandle.USER_ALL;
        ComponentName com = intent.getComponent();
        String name = com.getPackageName();
        mAm.killApplication(name,0,userId,null);
//        mAm.killApplicationProcess(name,userId);
    }
    private void runStart(Intent intent) throws Exception {
        mAm = ActivityManagerNative.getDefault();
        mWaitOption = true;

        if (mUserId == UserHandle.USER_ALL) {
            System.err.println("Error: Can't start service with user 'all'");
            return;
        }
        String mimeType = intent.getType();
        if (mimeType == null && intent.getData() != null
                && "content".equals(intent.getData().getScheme())) {
            mimeType = mAm.getProviderMimeType(intent.getData(), mUserId);
        }
        do {
            System.out.println("Starting: " + intent);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            ParcelFileDescriptor fd = null;

            int res;
            final long startTime = SystemClock.uptimeMillis();

            if (mStackId != INVALID_STACK_ID) {
                options = ActivityOptions.makeBasic();
                options.setLaunchStackId(mStackId);
            }
            if (mWaitOption) {
                result = mAm.startActivityAndWait(null, null, intent, mimeType,
                        null, null, 0, mStartFlags, profilerInfo,
                        options != null ? options.toBundle() : null, mUserId);
                res = result.result;

            } else {
                res = mAm.startActivityAsUser(null, null, intent, mimeType,
                        null, null, 0, mStartFlags, profilerInfo,
                        options != null ? options.toBundle() : null, mUserId);
            }
            final long endTime = SystemClock.uptimeMillis();
            PrintStream out = mWaitOption ? System.out : System.err;
            boolean launched = false;

            //------------------------保存数据到数据库--------------------------------------------------
            MyDatabaseHelper dbHelper = new MyDatabaseHelper(MainActivity.this,"Activities.db",null,2);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("Activity",result.who.flattenToShortString());
            values.put("Status","ok");
            values.put("ThisTime",result.thisTime);
            values.put("TotalTime",result.totalTime);
            values.put("WaitTime",endTime-startTime);
            Log.d(TAG, "onItemClick: 插入数据成功");
            db.insert("Activity",null,values);
            Log.d(TAG, "onItemClick: 插入数据成功"+db);
            //----------------------------------------------------------------------------------------

            switch (res) {
                case ActivityManager.START_SUCCESS:
                    launched = true;
                    break;
                case ActivityManager.START_SWITCHES_CANCELED:
                    launched = true;
                    out.println(
                            "Warning: Activity not started because the "
                                    + " current activity is being kept for the user.");
                    break;
                case ActivityManager.START_DELIVERED_TO_TOP:
                    launched = true;
                    out.println(
                            "Warning: Activity not started, intent has "
                                    + "been delivered to currently running "
                                    + "top-most instance.");
                    break;
                case ActivityManager.START_RETURN_INTENT_TO_CALLER:
                    launched = true;
                    out.println(
                            "Warning: Activity not started because intent "
                                    + "should be handled by the caller");
                    break;
                case ActivityManager.START_TASK_TO_FRONT:
                    launched = true;
                    out.println(
                            "Warning: Activity not started, its current "
                                    + "task has been brought to the front");
                    break;
                case ActivityManager.START_INTENT_NOT_RESOLVED:
                    out.println(
                            "Error: Activity not started, unable to "
                                    + "resolve " + intent.toString());
                    break;
                case ActivityManager.START_CLASS_NOT_FOUND:
                    out.println(NO_CLASS_ERROR_CODE);
                    out.println("Error: Activity class " +
                            intent.getComponent().toShortString()
                            + " does not exist.");
                    break;
                case ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT:
                    out.println(
                            "Error: Activity not started, you requested to "
                                    + "both forward and receive its result");
                    break;
                case ActivityManager.START_PERMISSION_DENIED:
                    out.println(
                            "Error: Activity not started, you do not "
                                    + "have permission to access it.");
                    break;
                case ActivityManager.START_NOT_VOICE_COMPATIBLE:
                    out.println(
                            "Error: Activity not started, voice control not allowed for: "
                                    + intent);
                    break;
                case ActivityManager.START_NOT_CURRENT_USER_ACTIVITY:
                    out.println(
                            "Error: Not allowed to start background user activity"
                                    + " that shouldn't be displayed for all users.");
                    break;
                default:
                    out.println(
                            "Error: Activity not started, unknown error code " + res);
                    break;
            }
            if (mWaitOption && launched) {
                if (result == null) {
                    result = new IActivityManager.WaitResult();
                    result.who = intent.getComponent();
                }
                System.out.println("Status: " + (result.timeout ? "timeout" : "ok"));
                if (result.who != null) {
                    System.out.println("Activity: " + result.who.flattenToShortString());
                }
                if (result.thisTime >= 0) {
                    System.out.println("ThisTime: " + result.thisTime);
                }
                if (result.totalTime >= 0) {
                    System.out.println("TotalTime: " + result.totalTime);
                }
                System.out.println("WaitTime: " + (endTime-startTime));
                System.out.println("Complete");
            }
            mRepeat--;
            if (mRepeat > 1) {
                mAm.unhandledBack();
            }
        } while (mRepeat > 1);

    }



    private class ClippedDrawable extends Drawable {
        private final Drawable mWallpaper;

        public ClippedDrawable(Drawable wallpaper) {
            mWallpaper = wallpaper;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            mWallpaper.setBounds(left, top, left + mWallpaper.getIntrinsicWidth(),
                    top + mWallpaper.getIntrinsicHeight());
//                mWallpaper.setBounds(1000000,50,50,50);
        }

        public void draw(Canvas canvas) {
            mWallpaper.draw(canvas);
        }

        public void setAlpha(int alpha) {
            mWallpaper.setAlpha(alpha);
        }

        public void setColorFilter(ColorFilter cf) {
            mWallpaper.setColorFilter(cf);
        }

        public int getOpacity() {
            return mWallpaper.getOpacity();
        }
    }
}
