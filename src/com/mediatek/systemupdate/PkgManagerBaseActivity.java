package com.mediatek.systemupdate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.View;
import android.widget.TextView;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.io.File;
import java.util.List;

class PkgManagerBaseActivity extends Activity {

    private static final String TAG = "SystemUpdate/PkgManagerBase";
    private static final String REBOOT_INTENT = "com.mediatek.intent.systemupdate.RebootRecoveryService";
    private static final String WRITE_COMMAND_INTENT = "com.mediatek.intent.systemupdate.WriteCommandService";
    private static final String COMMAND_PART2 = "COMMANDPART2";
    private static final String OTA_PATH_IN_RECOVERY_PRE = "/sdcard/";

    protected void fillPkgInfo(String strAndroidNum, String strVerNum, long size, String strPath) {

        View viewPkgInfo = findViewById(R.id.pkgInfo);
        if (viewPkgInfo != null) {

            TextView viewAndroidNum = (TextView) findViewById(R.id.textAndroidNum);
            viewAndroidNum.setText(strAndroidNum);
            TextView viewVerNum = (TextView) findViewById(R.id.textVerNum);
            viewVerNum.setText("Version " + strVerNum);
            TextView viewPkgSize = (TextView) findViewById(R.id.textPkgSize);

            if (viewPkgSize != null) {

                if (size == -1) {
                    viewPkgSize.setVisibility(View.GONE);
                } else if (size >= Util.M_SIZE) {
                    viewPkgSize.setText(getString(R.string.size_M, (double) size / Util.M_SIZE));
                } else if (size >= Util.K_SIZE) {
                    viewPkgSize.setText(getString(R.string.size_K, (double) size / Util.K_SIZE));
                } else {
                    viewPkgSize.setText(getString(R.string.size_B, size));
                }

            }

            TextView viewPkgPath = (TextView) findViewById(R.id.textPath);

            if (viewPkgPath != null) {
            	
            	if (strPath != null) {
                    viewPkgPath.setText(strPath);         		
            	} else {
            		viewPkgPath.setVisibility(View.GONE);
            	}

            } 

        }

    }

    protected void fillReleaseNotes(List<String> listNotes) {

        TextView viewNotes = (TextView) findViewById(R.id.textNotesDetail);

        if (viewNotes != null) {

            StringBuilder strNotes = new StringBuilder();

            for (String strItem : listNotes) {

                strNotes.append("- ").append(strItem).append("\n");

            }

            viewNotes.setText(strNotes);
        }

    }

    protected void installPackage(String pathname) {

        InstallPkgThread installThread = new InstallPkgThread(pathname);

        installThread.start();
    }

    protected boolean checkUpgradePackage() {
        return true;
    }

    protected void requeryPackages() {
        Xlog.d(TAG, "requery Packages");
        DownloadInfo.getInstance(getApplicationContext()).resetDownloadInfo();
        Intent i = new Intent(this, MainEntry.class);
        this.startActivity(i);
        this.finish();
    }

    protected void notifyUserInstall() {
    	return;
    }
    private boolean setRebootRecoveryFlag(String strPkgPath) {
        Xlog.i(TAG, "onSetRebootRecoveryFlag");

        try {
            IBinder binder = ServiceManager.getService("GoogleOtaBinder");
            SystemUpdateBinder agent = SystemUpdateBinder.Stub.asInterface(binder);

            if (agent == null) {
                Xlog.e(TAG, "agent is null");
                return false;
            }

            if (FeatureOption.MTK_EMMC_SUPPORT) {
                if (!agent.clearUpdateResult()) {
                    Xlog.e(TAG, "clearUpdateResult() false");
                    return false;
                }
            }

            if (!agent.setRebootFlag()) {
                Xlog.e(TAG, "setRebootFlag() false");
                return false;
            }

            DownloadInfo dlInfo = DownloadInfo.getInstance(getApplicationContext());
            dlInfo.setUpgradeStartedState(true);

            Intent intent = new Intent(WRITE_COMMAND_INTENT);
            intent.putExtra(COMMAND_PART2, OTA_PATH_IN_RECOVERY_PRE + strPkgPath);
            startService(intent);
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    class InstallPkgThread extends Thread {

        private String mPkgPath;

        public InstallPkgThread(String strPkgPath) {
            mPkgPath = strPkgPath;
        }

        /**
         * Main executing function of this thread.
         */
        public void run() {
            if (checkUpgradePackage() && setRebootRecoveryFlag(mPkgPath)) {
            	notifyUserInstall();
                startService(new Intent(REBOOT_INTENT));
            } else {
                return;
            }
        }

    }

}
