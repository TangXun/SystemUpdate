package com.mediatek.systemupdate;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.mediatek.xlog.Xlog;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

public class SdPkgInstallActivity extends PkgManagerBaseActivity implements
        DialogInterface.OnCancelListener, DialogInterface.OnKeyListener {

    private static final String TAG = "SystemUpdate/SdPkgInstall";

    public static final String KEY_ANDROID_NUM = "key_android_num";
    public static final String KEY_VERSION = "key_version";
    public static final String KEY_PATH = "key_path";
    public static final String KEY_NOTES = "key_notes";
    public static final String KEY_ORDER = "key_order";
    private static final String UPDATE_PACKAGE_NAME = Util.PathName.SD_INSTALL_PACKAGE;
    private static final String DES_PACKAGE = Util.PathName.OTA_PKG_FOLDER + File.separator
            + Util.PathName.SD_INSTALL_PACKAGE;

    private static final int DIALOG_INSTALLWARNING = 0;
    private static final int DIALOG_NOENOUGHSPACE = 1;
    private static final int DIALOG_NOSDCARD = 2;
    private static final int DIALOG_OTARESULT = 4;
    private static final int DIALOG_UNZIPPING = 5;
    private static final int DIALOG_UNKNOWN_ERROR = 6;
    private static final int DIALOG_SDCARDMOUNTED = 7;
    private static final int DIALOG_INVALIDATEPACKAGE = 8;

    private static final int MENU_ID_UPGRADE = Menu.FIRST;
    private static final int MENU_ID_REFRESH = Menu.FIRST + 1;

    private String mPath;
    private String mInstallPath;
    private String mVersion;
    private int mActivityOrder;
    private int mOTAresult;
    private int mOTADialogTitleResId;
    private int mOTADialogMessageResId;
    private ProgressDialog mUnzipProgressDialog;

    private DownloadInfo mDownloadInfo;
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Xlog.i(TAG, "handleMessage msg.what = " + msg.what);

            switch (msg.what) {
            case SystemUpdateService.MSG_SDCARDUNKNOWNERROR:
                // dismissDialog(mUnzipProgressDialog);
                if (mUnzipProgressDialog != null) {
                    mUnzipProgressDialog.dismiss();
                }

                showDialog(DIALOG_NOSDCARD);
                break;
            case SystemUpdateService.MSG_SDCARDINSUFFICENT:
                // dismissDialog(mUnzipProgressDialog);
                if (mUnzipProgressDialog != null) {
                    mUnzipProgressDialog.dismiss();
                }
                showDialog(DIALOG_NOENOUGHSPACE);
                break;
            case SystemUpdateService.MSG_SDCARDPACKAGEINVALIDATE:
                showDialog(DIALOG_INVALIDATEPACKAGE);
                /*
                 * case SystemUpdateService.MSG_SDCARDCRASHORUNMOUNT: String path = (String)
                 * msg.obj; if (path == null || mPath.contains(path)) {
                 * SdPkgInstallActivity.this.finish(); }
                 */
            case SystemUpdateService.MSG_UNKNOWERROR:
                showDialog(DIALOG_UNKNOWN_ERROR);
                break;
            case SystemUpdateService.MSG_OTA_PACKAGEERROR:
                mOTADialogTitleResId = R.string.package_error_title;
                mOTADialogMessageResId = R.string.package_error_message_invalid;
                showDialog(DIALOG_OTARESULT);
                break;
            case SystemUpdateService.MSG_OTA_NEEDFULLPACKAGE:
                mOTADialogTitleResId = R.string.package_error_title;
                mOTADialogMessageResId = R.string.package_error_message_full;
                showDialog(DIALOG_OTARESULT);
                break;
            case SystemUpdateService.MSG_OTA_SDCARDERROR:
                mOTADialogTitleResId = R.string.package_error_title;
                mOTADialogMessageResId = R.string.unmount_sdcard;
                showDialog(DIALOG_OTARESULT);
                break;
            case SystemUpdateService.MSG_OTA_USERDATAERROR:
                mOTADialogTitleResId = R.string.package_error_title;
                mOTADialogMessageResId = R.string.package_error_message_crash;
                showDialog(DIALOG_OTARESULT);
                break;
            case SystemUpdateService.MSG_OTA_SDCARDINFUFFICENT:
                mOTADialogTitleResId = R.string.package_error_title;
                mOTADialogMessageResId = R.string.insufficient_space;
                showDialog(DIALOG_OTARESULT);
                break;
            case SystemUpdateService.MSG_OTA_USERDATAINSUFFICENT:
                mOTADialogTitleResId = R.string.package_error_title;
                mOTADialogMessageResId = R.string.package_error_message_insuff;
                showDialog(DIALOG_OTARESULT);
                break;
            case SystemUpdateService.MSG_UNZIP_LODING:
                showDialog(DIALOG_UNZIPPING);
                break;
            case SystemUpdateService.MSG_CKSUM_ERROR:
            case SystemUpdateService.MSG_UNZIP_ERROR:
                if (mUnzipProgressDialog != null) {
                    mUnzipProgressDialog.dismiss();
                }
                mOTADialogTitleResId = R.string.package_unzip_error;
                mOTADialogMessageResId = R.string.package_error_message_invalid;
                showDialog(DIALOG_OTARESULT);
                break;
            case SystemUpdateService.MSG_OTA_CLOSECLIENTUI:
                SdPkgInstallActivity.this.finish();
                break;
            default:
                super.handleMessage(msg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Xlog.i(TAG, "onCreate");

        setContentView(R.layout.sd_package_ready);
        this.getActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        String strAndroidNum = intent.getStringExtra(KEY_ANDROID_NUM);
        mVersion = intent.getStringExtra(KEY_VERSION);
        mPath = intent.getStringExtra(KEY_PATH);
        mActivityOrder = intent.getIntExtra(KEY_ORDER, 0);
        String strNotes = intent.getStringExtra(KEY_NOTES);

        if (strAndroidNum != null && mVersion != null && mPath != null) {
            fillPkgInfo(strAndroidNum, mVersion, -1L, mPath);
        }
        if (strNotes != null) {
            List<String> listNotes = Arrays.asList(strNotes.split(File.separator));
            fillReleaseNotes(listNotes);
        }

        String packagePath = Util.getPackagePathName(this);
        if (packagePath != null) {
            mInstallPath = packagePath + File.separator + Util.PathName.SD_INSTALL_PACKAGE;
        }
        mDownloadInfo = DownloadInfo.getInstance(getApplicationContext());

        IntentFilter filter = new IntentFilter();
        filter.addAction(Util.Action.ACTION_MEDIA_MOUNT_UPDATEUI);
        filter.addAction(Util.Action.ACTION_MEDIA_UNMOUNT_UPDATEUI);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        Xlog.i(TAG, "onDestroy");

        unregisterReceiver(mReceiver);

        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        Xlog.i(TAG, "onCreateDialog id, dialog id = " + id);
        switch (id) {
        case DIALOG_INSTALLWARNING:// warning user before install
            return new AlertDialog.Builder(this).setTitle(R.string.install_sd_title)
                    .setMessage(getString(R.string.install_sd_message))
                    .setPositiveButton(R.string.btn_install, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int whichButton) {
                            invalidateOptionsMenu();
                            mDownloadInfo.setActivityID(mActivityOrder);
                            mDownloadInfo.setVerNum(mVersion);
                            mDownloadInfo.setDLSessionStatus(DownloadInfo.STATE_SDINSTALL_START);
                            if (MainEntry.getInstance() != null) {
                                MainEntry.getInstance().finish();
                            }
                            installPackage(DES_PACKAGE);
                        }
                    })
                    .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            SdPkgInstallActivity.this.finish();
                        }
                    }).create();
        case DIALOG_NOSDCARD: // error occur before unzip update.zip to correct SD card

            return new AlertDialog.Builder(this).setTitle(R.string.error_sdcard)
                    .setMessage(getString(R.string.sdcard_crash_or_unmount))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Util.deleteFile(mInstallPath);
                            mDownloadInfo.resetDownloadInfo();
                            SdPkgInstallActivity.this.finish();
                        }
                    }).create();
        case DIALOG_NOENOUGHSPACE:// error occur before unzip update.zip to correct SD card
            return new AlertDialog.Builder(this).setTitle(R.string.insufficient_space_title)
                    .setMessage(getString(R.string.install_sd_info))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            SdPkgInstallActivity.this.finish();
                        }
                    }).create();
        case DIALOG_UNZIPPING:
            mUnzipProgressDialog = new ProgressDialog(this);
            mUnzipProgressDialog.setIndeterminate(true);
            mUnzipProgressDialog.setCancelable(false);
            mUnzipProgressDialog.setProgressStyle(android.R.attr.progressBarStyleSmall);
            mUnzipProgressDialog.setMessage(getString(R.string.installing_message));
            mUnzipProgressDialog.show();
            mUnzipProgressDialog.setOnKeyListener(this);
            mUnzipProgressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    mUnzipProgressDialog = null;
                }
            });
            return mUnzipProgressDialog;
        case DIALOG_UNKNOWN_ERROR:
            return new AlertDialog.Builder(this).setTitle(R.string.unknown_error)
                    .setMessage(getString(R.string.unknown_error_content))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            SdPkgInstallActivity.this.finish();
                        }
                    }).create();
        case DIALOG_OTARESULT:

            return new AlertDialog.Builder(this).setTitle(mOTADialogTitleResId)
                    .setMessage(getString(mOTADialogMessageResId))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Util.deleteFile(mInstallPath);
                            mDownloadInfo.resetDownloadInfo();
                            SdPkgInstallActivity.this.finish();
                        }
                    }).setOnKeyListener(new DialogInterface.OnKeyListener() {
                        public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                            if (keyCode == KeyEvent.KEYCODE_BACK) {
                                Util.deleteFile(mInstallPath);
                                mDownloadInfo.resetDownloadInfo();
                                SdPkgInstallActivity.this.finish();
                                return true;
                            }
                            return false;
                        }
                    }).create();
            // M: add by mtk80800, it will show before install
        case DIALOG_INVALIDATEPACKAGE:
            return new AlertDialog.Builder(this).setTitle(getString(R.string.package_error_title))
                    .setMessage(getString(R.string.package_error_message_invalid))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            mDownloadInfo.resetDownloadInfo();
                            SdPkgInstallActivity.this.finish();
                        }
                    }).setNegativeButton(android.R.string.no, null).create();
        case DIALOG_SDCARDMOUNTED:

            return new AlertDialog.Builder(this).setTitle(getString(R.string.error_sdcard))
                    .setMessage(getString(R.string.sdcard_inserted))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            requeryPackages();
                        }
                    }).setNegativeButton(android.R.string.no, null).create();
            // M: end add
        default:
            break;
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_ID_UPGRADE, 0, R.string.btn_install).setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS);

        if (mDownloadInfo.getIfNeedRefreshMenu() && mDownloadInfo.getActivityID() < 0) {
            menu.add(0, MENU_ID_REFRESH, 0, R.string.menu_stats_refresh)
                    .setIcon(R.drawable.ic_menu_refresh_holo_dark)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ID_UPGRADE:
            showDialog(DIALOG_INSTALLWARNING);
            return true;
        case android.R.id.home:

            finish();
            break;
        case MENU_ID_REFRESH:
            requeryPackages();
            break;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean checkUpgradePackage() {
        if (!checkSdCard()) {
            Xlog.w(TAG, "[checkUpgradePackage], checkSdCardOk fail");
            return false;
        }

        if (!unzipInstallFile()) {
            Xlog.w(TAG, "[checkUpgradePackage], unzipInstallFile fail");
            return false;
        }

        // check ota
        CheckPkg checkPkg = new CheckPkg(this, UpgradePkgManager.SD_PACKAGE, mInstallPath);
        mOTAresult = checkPkg.execForResult();
        Xlog.i(TAG, "[checkUpgradePackage], check_ota result = " + mOTAresult);
        if (mOTAresult == Util.OTAresult.CHECK_OK) {
            return true;
        } else {
            sendCheckOTAMessage();
            return false;
        }
    }

    private boolean checkSdCard() {
        // check sdcard space
        if (Util.isSdcardAvailable(this)) {
            File f = new File(mPath);
            if (f.exists()) {
                long insufficientSpace = Util.getExtraSpaceNeeded(this, (long) (1.5 * f.length()));
                if (insufficientSpace < 0) {
                    if (mHandler != null) {
                        mHandler.sendEmptyMessage(SystemUpdateService.MSG_SDCARDINSUFFICENT);
                    }
                    return false;
                }
            } else {
                if (mHandler != null) {
                    mHandler.sendEmptyMessage(SystemUpdateService.MSG_SDCARDPACKAGEINVALIDATE);
                }
            }
        } else {
            if (mHandler != null) {
                mHandler.sendEmptyMessage(SystemUpdateService.MSG_SDCARDUNKNOWNERROR);
            } else {
                Util.deleteFile(mInstallPath);
                mDownloadInfo.resetDownloadInfo();
            }
            return false;
        }

        return true;
    }

    private boolean unzipInstallFile() {
        if (mHandler != null) {
            mHandler.sendEmptyMessage(SystemUpdateService.MSG_UNZIP_LODING);
        }
        int result = -1;

        String updateZip = Util.getTempPath(this) + Util.PathName.PACKAGE_NAME;
        try {
            ZipFile sdPackage = new ZipFile(mPath);
            result = Util.unzipFileElement(sdPackage, Util.PathName.PACKAGE_NAME.substring(1),
                    updateZip);
            sdPackage.close();
            Xlog.d(TAG, "[unzipInstallFile], unzip update.zip to temp folder");
            if (result == Util.OTAresult.OTA_FILE_UNZIP_OK) {
                ZipFile updatePackage = new ZipFile(updateZip);
                result = Util.unzipFileElement(updatePackage, UPDATE_PACKAGE_NAME, mInstallPath);
                updatePackage.close();
                Xlog.d(TAG, "[unzipInstallFile], unzip install.zip to googleota folder");
            }
        } catch (IOException e) {
            Xlog.e(TAG, "[unzipInstallFile], unzip file fail");
            e.printStackTrace();
        }

        Util.deleteFile(updateZip);

        if (result != Util.OTAresult.OTA_FILE_UNZIP_OK) {
            if (mHandler != null) {
                mHandler.sendEmptyMessage(SystemUpdateService.MSG_UNZIP_ERROR);
            }
            return false;
        }

        return true;
    }

    private void sendCheckOTAMessage() {
        Xlog.i(TAG, "sendCheckOTAMessage, mOTAresult = " + mOTAresult);
        switch (mOTAresult) {
        case Util.OTAresult.CHECK_OK:
            if (mHandler != null) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(SystemUpdateService.MSG_OTA_CLOSECLIENTUI));
            }
            break;

        case Util.OTAresult.ERROR_ONLY_FULL_CHANGE_SIZE:
            if (mHandler != null) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(SystemUpdateService.MSG_OTA_NEEDFULLPACKAGE));
            } else {
                Util.deleteFile(mInstallPath);
                mDownloadInfo.resetDownloadInfo();
            }
            // send message to prompt user delta is proper and delete delta
            break;
        case Util.OTAresult.ERROR_ACCESS_SD:
        case Util.OTAresult.ERROR_SD_WRITE_PROTECTED:

            if (mHandler != null) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(SystemUpdateService.MSG_OTA_SDCARDERROR));
            }
            // send message to prompt user sdcard error and need check
            break;
        case Util.OTAresult.ERROR_ACCESS_USERDATA:

            if (mHandler != null) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(SystemUpdateService.MSG_OTA_USERDATAERROR));
            }
            // send message to prompt user user data partition error and
            // delete image
            break;
        case Util.OTAresult.ERROR_SD_FREE_SPACE:

            if (mHandler != null) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(SystemUpdateService.MSG_OTA_SDCARDINFUFFICENT));
            }
            // send message to prompt user sdcard insufficent and need to
            // delete some file form sdcard
            break;
        case Util.OTAresult.ERROR_USERDATA_FREE_SPACE:
            if (mHandler != null) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(SystemUpdateService.MSG_OTA_USERDATAINSUFFICENT));
            }
            // send message to prompt user user data insufficent and need to
            // delete some file form sdcard
            break;
        default:
            if (mHandler != null) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(SystemUpdateService.MSG_OTA_PACKAGEERROR));
            } else {
                Util.deleteFile(mInstallPath);
                mDownloadInfo.resetDownloadInfo();
            }
            // send message to prompt unknown error and delete delta.
            break;
        }
    }

    private void sendCheckOTAMessage() {
	    Xlog.i(TAG, "sendCheckOTAMessage, mOTAresult = " + mOTAresult);
	    switch (mOTAresult) {
	    case Util.OTAresult.CHECK_OK:
	        if (mHandler != null) {
	            mHandler.sendMessage(mHandler
	                    .obtainMessage(SystemUpdateService.MSG_OTA_CLOSECLIENTUI));
	        }
	        break;
	
	    case Util.OTAresult.ERROR_ONLY_FULL_CHANGE_SIZE:
	        if (mHandler != null) {
	            mHandler.sendMessage(mHandler
	                    .obtainMessage(SystemUpdateService.MSG_OTA_NEEDFULLPACKAGE));
	        } else {
	            Util.deleteFile(mInstallPath);
	            mDownloadInfo.resetDownloadInfo();
	        }
	        // send message to prompt user delta is proper and delete delta
	        break;
	    case Util.OTAresult.ERROR_ACCESS_SD:
	    case Util.OTAresult.ERROR_SD_WRITE_PROTECTED:
	
	        if (mHandler != null) {
	            mHandler.sendMessage(mHandler
	                    .obtainMessage(SystemUpdateService.MSG_OTA_SDCARDERROR));
	        }
	        // send message to prompt user sdcard error and need check
	        break;
	    case Util.OTAresult.ERROR_ACCESS_USERDATA:
	
	        if (mHandler != null) {
	            mHandler.sendMessage(mHandler
	                    .obtainMessage(SystemUpdateService.MSG_OTA_USERDATAERROR));
	        }
	        // send message to prompt user user data partition error and
	        // delete image
	        break;
	    case Util.OTAresult.ERROR_SD_FREE_SPACE:
	
	        if (mHandler != null) {
	            mHandler.sendMessage(mHandler
	                    .obtainMessage(SystemUpdateService.MSG_OTA_SDCARDINFUFFICENT));
	        }
	        // send message to prompt user sdcard insufficent and need to
	        // delete some file form sdcard
	        break;
	    case Util.OTAresult.ERROR_USERDATA_FREE_SPACE:
	        if (mHandler != null) {
	            mHandler.sendMessage(mHandler
	                    .obtainMessage(SystemUpdateService.MSG_OTA_USERDATAINSUFFICENT));
	        }
	        // send message to prompt user user data insufficent and need to
	        // delete some file form sdcard
	        break;
	    default:
	        if (mHandler != null) {
	            mHandler.sendMessage(mHandler
	                    .obtainMessage(SystemUpdateService.MSG_OTA_PACKAGEERROR));
	        } else {
	            Util.deleteFile(mInstallPath);
	            mDownloadInfo.resetDownloadInfo();
	        }
	        // send message to prompt unknown error and delete delta.
	        break;
	    }
	}

	@Override
    public void onCancel(DialogInterface dialog) {
        SdPkgInstallActivity.this.finish();
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            SdPkgInstallActivity.this.finish();
            return true;
        }
        return false;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Util.Action.ACTION_MEDIA_MOUNT_UPDATEUI.equals(action)) {
                // M: Add by mtk80800, sd card mounted, reminder user to refresh
                showDialog(DIALOG_SDCARDMOUNTED);

            } else if (Util.Action.ACTION_MEDIA_UNMOUNT_UPDATEUI.equals(action)) {
                // M: Add by mtk80800, sdcard unmount, reset download info and finish this activity
                String path = (String) intent.getExtra("storagePath");
                if (mPath.contains(path)) {
                    Xlog.w(TAG, "finish");
                    if (mDownloadInfo.getActivityID() < 0) {
                        Toast.makeText(context, R.string.sdcard_unmount, Toast.LENGTH_LONG).show();
                    }
                    SdPkgInstallActivity.this.finish();
                }
            }
        }
    };
}
