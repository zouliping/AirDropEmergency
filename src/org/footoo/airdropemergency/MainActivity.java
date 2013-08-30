package org.footoo.airdropemergency;

import java.util.Timer;
import java.util.TimerTask;

import org.footoo.airdropemergency.constvalue.ConstValue;
import org.footoo.airdropemergency.httpserver.ServerRunner;
import org.footoo.airdropemergency.util.FileAccessUtil;
import org.footoo.airdropemergency.util.ToastUtil;
import org.footoo.airdropemergency.util.Utils;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;

import com.slidingmenu.lib.SlidingMenu;

public class MainActivity extends BaseActivity {

	private static Boolean isExit = false;
	private static Boolean hasTask = false;
	private Timer tExit;
	private TimerTask task;

	private Handler handler;

	private String IPAddr;
	private WifiManager wifiManager;
	private WifiInfo wifiInfo;
	private boolean wifiIsAvailable = false;

	private Fragment mContent;

	private WifiBroadcastReceiver mReceiver;

	public MainActivity() {
		super(R.string.app_name);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			mContent = getSupportFragmentManager().getFragment(
					savedInstanceState, "mContent");
		}
		if (mContent == null) {
			mContent = new MainFragment();
		}

		// set the above view
		setContentView(R.layout.content_frame);
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.content_frame, mContent).commit();

		// set the behind view
		setBehindContentView(R.layout.menu_frame);
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.menu_frame, new BehindMenuFragment()).commit();

		// customize the slidemenu
		getSlidingMenu().setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);

		try {
			initData();
		} catch (Exception e) {
			ToastUtil.showShortToast(MainActivity.this,
					getString(R.string.unknown_error));
		}

		IntentFilter filter = new IntentFilter();
		filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
		filter.addAction("android.net.wifi.STATE_CHANGE");
		mReceiver = new WifiBroadcastReceiver();
		registerReceiver(mReceiver, filter);
	}

	@SuppressLint("HandlerLeak")
	private void initData() {
		// var used for double click to exit
		tExit = new Timer();
		task = new TimerTask() {
			@Override
			public void run() {
				isExit = false;
				hasTask = true;
			}
		};

		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				// get the ip address successfully, start the server
				Bundle bundle = msg.getData();
				Log.i("ip", bundle.getString("ip") + "");
				IPAddr = bundle.getString("ip");
				// update the address in main fragment
				if (mContent instanceof MainFragment) {
					((MainFragment) mContent).setAddrTvText(getBrowserAddr());
					((MainFragment) mContent)
							.setPromptTvText(getBrowserPrompt());
				}
				// start the server
				ServerRunner.startServer(ConstValue.PORT);
				// TODO notification
			}
		};

		// create the dir to store the received files
		ConstValue.BASE_DIR = FileAccessUtil.createDir(ConstValue.DIR_NAME);

		wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		wifiInfo = wifiManager.getConnectionInfo();
		// wifi is available?
		wifiIsAvailable = Utils.isWifiConnected(wifiManager, wifiInfo);
		if (wifiIsAvailable) {
			// wifi is available, get the ip address in a new thread
			new WifiAvailableThread().start();
		}
	}

	public class WifiAvailableThread extends Thread {
		@Override
		public void run() {
			Bundle bundle = new Bundle();
			bundle.putString("ip", Utils.int2Ip(wifiInfo.getIpAddress()));
			Message msg = new Message();
			msg.setData(bundle);
			handler.sendMessage(msg);
		}
	};

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		getSupportFragmentManager().putFragment(outState, "mContent", mContent);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (isExit == false) {
				isExit = true;
				ToastUtil
						.showShortToast(
								this,
								getResources().getString(
										R.string.double_click_to_exit));
				if (!hasTask) {
					tExit.schedule(task, 2000);
				}
			} else {
				ServerRunner.stopServer();
				finish();
				System.exit(0);
			}
		}
		return false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
	}

	/**
	 * called by the behind menu fragment
	 * 
	 * @param fragment
	 */
	public void switchContent(Fragment fragment) {
		mContent = fragment;
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.content_frame, fragment).commit();
		getSlidingMenu().showContent();
	}

	/**
	 * called by the Main Fragment
	 * 
	 * @return
	 */
	public String getBrowserAddr() {
		if (wifiIsAvailable) {
			return "http://" + IPAddr + ":" + ConstValue.PORT;
		} else {
			return "";
		}
	}

	/**
	 * called by the Main Fragment
	 * 
	 * @return
	 */
	public String getBrowserPrompt() {
		int promptId = wifiIsAvailable ? R.string.input_on_browser
				: R.string.wifi_not_avaliable;
		return getString(promptId);
	}

	/**
	 * response to wifi state changes
	 * 
	 * @author zouliping
	 * 
	 */
	private class WifiBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction()
					.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				int wifiState = intent.getIntExtra(
						WifiManager.EXTRA_WIFI_STATE,
						WifiManager.WIFI_STATE_DISABLED);
				if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
					wifiIsAvailable = true;
					new WifiAvailableThread().start();
				} else if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
					wifiIsAvailable = false;
					// refresh UI to show wifi is unavailable
					if (mContent instanceof MainFragment) {
						((MainFragment) mContent)
								.setAddrTvText(getBrowserAddr());
						((MainFragment) mContent)
								.setPromptTvText(getBrowserPrompt());
					}
					ServerRunner.stopServer();
				}
			}
		}
	}
}
