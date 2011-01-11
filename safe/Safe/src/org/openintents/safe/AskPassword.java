/* $Id$
 * 
 * Copyright 2007-2008 Steven Osborn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openintents.safe;

import java.io.File;
import java.security.NoSuchAlgorithmException;

import org.openintents.distribution.EulaActivity;
import org.openintents.util.VersionUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;



/**
 * AskPassword Activity
 * 
 * This activity just acts as a splash screen and gets the password from the
 * user that will be used to decrypt/encrypt password entries.
 * 
 * @author Steven Osborn - http://steven.bitsetters.com
 */
public class AskPassword extends Activity {

	private boolean debug = false;
	private static String TAG = "AskPassword";
	public static String EXTRA_IS_LOCAL = "org.openintents.safe.bundle.EXTRA_IS_REMOTE";

    public static final int REQUEST_RESTORE = 0;

    // Menu Item order
    public static final int SWITCH_MODE_INDEX = Menu.FIRST;
    public static final int MUTE_INDEX = Menu.FIRST + 1;
    
    public static final int VIEW_NORMAL = 0;
    public static final int VIEW_KEYPAD = 1;
    
    private int viewMode = VIEW_NORMAL;

	private EditText pbeKey;
	private DBHelper dbHelper=null;
	private TextView introText;
//	private TextView confirmText;
	private TextView remoteAsk;
	private EditText confirmPass;
	private String PBEKey;
	private String salt;
	private String masterKey;
	private CryptoHelper ch;
	private boolean firstTime = false;

	// Keypad variables
	private String keypadPassword="";
	
	private MediaPlayer mpDigitBeep = null;
	private MediaPlayer mpErrorBeep = null;
	private MediaPlayer mpSuccessBeep = null;
	private boolean mute=false;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
        
		if (!EulaActivity.checkEula(this, getIntent())) {
            return;
        }
			
		if (debug) Log.d(TAG,"onCreate("+icicle+")");

		dbHelper = new DBHelper(this);
			
		ch = new CryptoHelper();
		if (dbHelper.needsUpgrade()) {
			switch (dbHelper.fetchVersion()) {
			case 2:
				databaseVersionError();
			}
		}
		salt = dbHelper.fetchSalt();
		masterKey = dbHelper.fetchMasterKey();

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean prefKeypad = sp.getBoolean(Preferences.PREFERENCE_KEYPAD, false);
        boolean prefKeypadMute = sp.getBoolean(Preferences.PREFERENCE_KEYPAD_MUTE, false);
        mute=prefKeypadMute;
        
        if (prefKeypad) {
        	viewMode=VIEW_KEYPAD;
        }
		if (masterKey.length() == 0) {
			firstTime=true;
		}		
		if ((viewMode==VIEW_NORMAL) || (firstTime)) {
			normalInit();
		} else {
			keypadInit();
		}
	}
	
	private void normalInit() {
		// Setup layout
		setContentView(R.layout.front_door);
		ImageView icon = (ImageView) findViewById(R.id.entry_icon);
		icon.setImageResource(R.drawable.ic_launcher_safe);
		TextView header = (TextView) findViewById(R.id.entry_header);
		String version = VersionUtils.getVersionNumber(this);
		String appName = VersionUtils.getApplicationName(this);
		String head = appName + " " + version + "\n";
		header.setText(head);

		Intent thisIntent = getIntent();
		boolean isLocal = thisIntent.getBooleanExtra (EXTRA_IS_LOCAL, false);

		pbeKey = (EditText) findViewById(R.id.password);
		pbeKey.requestFocus();
		introText = (TextView) findViewById(R.id.first_time);
		remoteAsk = (TextView) findViewById(R.id.remote);
		confirmPass = (EditText) findViewById(R.id.pass_confirm);
//		confirmText = (TextView) findViewById(R.id.confirm_lbl);
		if (masterKey.length() == 0) {
			firstTime = true;
			introText.setVisibility(View.VISIBLE);
//			confirmText.setVisibility(View.VISIBLE);
			confirmPass.setVisibility(View.VISIBLE);
			checkForBackup();
		}
		if (! isLocal) {
			if (remoteAsk != null) {
				remoteAsk.setVisibility(View.VISIBLE);
			}
		}
		if (firstTime) {
			confirmPass.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			        if (actionId == EditorInfo.IME_ACTION_DONE) {
						handleContinue();
			            return true;
			        }
			        return false;
			    }
			});
		}else{
			pbeKey.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			        if (actionId == EditorInfo.IME_ACTION_DONE) {
						handleContinue();
			            return true;
			        }
			        return false;
			    }
			});
		}
		Button continueButton = (Button) findViewById(R.id.continue_button);

		continueButton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View arg0) {
				handleContinue();
			}
		});
	}
	
	private void handleContinue() {
		PBEKey = pbeKey.getText().toString();
		// For this version of CryptoHelper, we use the user-entered password.
		// All other versions should be instantiated with the generated master
		// password.

		// Password must be at least 4 characters
		if (PBEKey.length() < 4) {
			Toast.makeText(AskPassword.this, R.string.notify_blank_pass,
					Toast.LENGTH_SHORT).show();
		    Animation shake = AnimationUtils
	        .loadAnimation(AskPassword.this, R.anim.shake);
	        
	        findViewById(R.id.password).startAnimation(shake);
			return;
		}

		// If it's the user's first time to enter a password,
		// we have to store it in the database. We are going to
		// store an encrypted hash of the password.
		// Generate a master key, encrypt that with the pbekey
		// and store the encrypted master key in database.
		if (firstTime) {

			// Make sure password and confirm fields match
			if (pbeKey.getText().toString().compareTo(
					confirmPass.getText().toString()) != 0) {
				Toast.makeText(AskPassword.this,
						R.string.confirm_pass_fail, Toast.LENGTH_SHORT)
						.show();
				return;
			}
			try {
				salt = CryptoHelper.generateSalt();
				masterKey = CryptoHelper.generateMasterKey();
			} catch (NoSuchAlgorithmException e1) {
				e1.printStackTrace();
				Toast.makeText(AskPassword.this,getString(R.string.crypto_error)
					+ e1.getMessage(), Toast.LENGTH_SHORT).show();
				return;
			}
			if (debug) Log.i(TAG, "Saving Password: " + masterKey);
			try {
				ch.init(CryptoHelper.EncryptionStrong,salt);
				ch.setPassword(PBEKey);
				String encryptedMasterKey = ch.encrypt(masterKey);
				dbHelper.storeSalt(salt);
				dbHelper.storeMasterKey(encryptedMasterKey);
			} catch (CryptoHelperException e) {
				Log.e(TAG, e.toString());
				Toast.makeText(AskPassword.this,getString(R.string.crypto_error)
					+ e.getMessage(), Toast.LENGTH_SHORT).show();
				return;
			}
		} else if (!checkUserPassword(PBEKey)) {
			// Check the user's password and display a
			// message if it's wrong
			Toast.makeText(AskPassword.this, R.string.invalid_password,
					Toast.LENGTH_SHORT).show();
	        Animation shake = AnimationUtils
	        .loadAnimation(AskPassword.this, R.anim.shake);
	        
	        findViewById(R.id.password).startAnimation(shake);
			return;
		}
		gotPassword();
	}
	
	private void gotPassword() {
		Intent callbackIntent = new Intent();
		
		// Return the master key to our caller.  We no longer need the
		// user-entered PBEKey. The master key is used for everything
		// from here on out.
		if (debug) Log.d(TAG,"calbackintent: masterKey="+masterKey+" salt="+salt);
		callbackIntent.putExtra("masterKey", masterKey);
		callbackIntent.putExtra("salt", salt);
		setResult(RESULT_OK, callbackIntent);
		
		finish();
	}
	
	private void checkForBackup() {
    	File externalStorageDirectory=Environment.getExternalStorageDirectory();
    	String backupFullname=externalStorageDirectory.getAbsolutePath()+"/"+
    		CategoryList.BACKUP_BASENAME+".xml";
		File restoreFile=new File(backupFullname);
		if (!restoreFile.exists()) {
			return;
		}
		Button restoreButton = (Button) findViewById(R.id.restore_button);
		if (restoreButton == null) {
			if (debug) Log.d(TAG, "layout not created yet");
			return;
		}
		restoreButton.setVisibility(View.VISIBLE);
		restoreButton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View arg0) {
				Intent restore = new Intent(AskPassword.this, Restore.class);
				restore.putExtra(Restore.KEY_FIRST_TIME, true);
				startActivityForResult(restore,REQUEST_RESTORE);		
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (debug) Log.d(TAG, "onPause()");

		if (dbHelper!=null) {
			dbHelper.close();
			dbHelper = null;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (debug) Log.d(TAG,"onDestroy()");
		keypadOnDestroy();
	}

	@Override
	protected void onResume() {
		super.onPause();

		if (debug) Log.d(TAG, "onResume()");
		if (CategoryList.isSignedIn()==true) {
			if (debug) Log.d(TAG,"already signed in");
			Intent callbackIntent = new Intent();
			callbackIntent.putExtra("salt", CategoryList.getSalt());
			callbackIntent.putExtra("masterKey", CategoryList.getMasterKey());
			setResult(RESULT_OK, callbackIntent);
			finish();
			return;
		}

		if (dbHelper == null) {
			dbHelper = new DBHelper(this);
		}
		if (viewMode==VIEW_NORMAL) {
			// clear pbeKey in case user had typed it, strayed
			// to something else, then another person opened
			// the app.   Wouldn't want the password already typed
			pbeKey.setText("");
		}

	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			MenuItem miSwitch = menu.findItem(SWITCH_MODE_INDEX);
			if (firstTime) {
				miSwitch.setEnabled(false);
			} else {
				miSwitch.setEnabled(true);
			}
			MenuItem miMute = menu.findItem(MUTE_INDEX);
			if (viewMode==VIEW_KEYPAD) {
				miMute.setVisible(true);
				if (mute) {
					miMute.setTitle(R.string.sounds);
					miMute.setIcon(android.R.drawable.ic_lock_silent_mode_off);
				} else {
					miMute.setTitle(R.string.mute);
					miMute.setIcon(android.R.drawable.ic_lock_silent_mode);
				}
			} else {
				miMute.setVisible(false);
			}
		}
		return super.onMenuOpened(featureId, menu);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
	
		menu.add(0, SWITCH_MODE_INDEX, 0, R.string.switch_mode)
			.setIcon(android.R.drawable.ic_menu_directions);

		MenuItem miMute;
		if (mute) {
			miMute=menu.add(0, MUTE_INDEX, 0, R.string.sounds)
				.setIcon(android.R.drawable.ic_lock_silent_mode_off);
		} else {
			miMute=menu.add(0, MUTE_INDEX, 0, R.string.mute)
				.setIcon(android.R.drawable.ic_lock_silent_mode);
		}
		miMute.setVisible(viewMode==VIEW_KEYPAD);
		
		return super.onCreateOptionsMenu(menu);
    }
	
	private void switchView() {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor spe=sp.edit();
		if (viewMode==VIEW_NORMAL) {
			viewMode=VIEW_KEYPAD;
			spe.putBoolean(Preferences.PREFERENCE_KEYPAD, true);
			keypadInit();
		} else {
			viewMode=VIEW_NORMAL;
			spe.putBoolean(Preferences.PREFERENCE_KEYPAD, false);
			normalInit();
		}
		if (!spe.commit()) {
			if (debug) Log.d(TAG,"commitment issues");
		}
	}
	
    public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case SWITCH_MODE_INDEX:
			switchView();
			break;
		case MUTE_INDEX:
			SharedPreferences msp = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor mspe=msp.edit();
	        mspe.putBoolean(Preferences.PREFERENCE_KEYPAD_MUTE, !mute);
	        mute=!mute;
			if (!mspe.commit()) {
				if (debug) Log.d(TAG,"mute commitment issues");
			}
			break;
		default:
			Log.e(TAG,"Unknown itemId");
			break;
		}
		return super.onOptionsItemSelected(item);
    }

	private void databaseVersionError() {
		Dialog about = new AlertDialog.Builder(this)
		.setIcon(R.drawable.passicon)
		.setTitle(R.string.database_version_error_title)
		.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				setResult(RESULT_CANCELED);
				finish();
			}
		})
		.setMessage(R.string.database_version_error_msg)
		.create();
		about.show();

	}
	/**
	 * 
	 * @return
	 */
	private boolean checkUserPassword(String password) {
		String encryptedMasterKey = dbHelper.fetchMasterKey();
		String decryptedMasterKey = "";
		if (debug) Log.d(TAG,"checkUserPassword: encryptedMasterKey="+encryptedMasterKey);
		try {
			ch.init(CryptoHelper.EncryptionStrong,salt);
			ch.setPassword(password);
			decryptedMasterKey = ch.decrypt(encryptedMasterKey);
			if (debug) Log.d(TAG,"decryptedMasterKey="+decryptedMasterKey);
		} catch (CryptoHelperException e) {
			Log.e(TAG, e.toString());
		}
		if (ch.getStatus()==true) {
			masterKey=decryptedMasterKey;
			return true;
		}
		return false;
	}
	
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent i) {
    	super.onActivityResult(requestCode, resultCode, i);

    	if ((requestCode== REQUEST_RESTORE) && (resultCode == RESULT_OK)) {
    		Log.d(TAG,"returning masterkey: "+CategoryList.getMasterKey());
			Intent callbackIntent = new Intent();
			callbackIntent.putExtra("salt", CategoryList.getSalt());
			callbackIntent.putExtra("masterKey", CategoryList.getMasterKey());
			setResult(RESULT_OK, callbackIntent);
    		finish();
    	}
    }

	/////////////// Keypad Functions /////////////////////

	private void keypadInit() {
		if (mpDigitBeep==null) {
			mpDigitBeep = MediaPlayer.create(this, R.raw.dtmf2a);
			mpErrorBeep = MediaPlayer.create(this, R.raw.click6a);
			mpSuccessBeep = MediaPlayer.create(this, R.raw.dooropening1);
		}

		keypadPassword="";
    	
		setContentView(R.layout.keypad);

		TextView header = (TextView) findViewById(R.id.entry_header);
		String version = VersionUtils.getVersionNumber(this);
		String appName = VersionUtils.getApplicationName(this);
		String head = appName + " " + version;
		header.setText(head);

		Button keypad1 = (Button) findViewById(R.id.keypad1);
		keypad1.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "1";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad2 = (Button) findViewById(R.id.keypad2);
		keypad2.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "2";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad3 = (Button) findViewById(R.id.keypad3);
		keypad3.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "3";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad4 = (Button) findViewById(R.id.keypad4);
		keypad4.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "4";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad5 = (Button) findViewById(R.id.keypad5);
		keypad5.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "5";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad6 = (Button) findViewById(R.id.keypad6);
		keypad6.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "6";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad7 = (Button) findViewById(R.id.keypad7);
		keypad7.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "7";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad8 = (Button) findViewById(R.id.keypad8);
		keypad8.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "8";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad9 = (Button) findViewById(R.id.keypad9);
		keypad9.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "9";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypadStar = (Button) findViewById(R.id.keypad_star);
		keypadStar.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "*";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypad0 = (Button) findViewById(R.id.keypad0);
		keypad0.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "0";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypadPound = (Button) findViewById(R.id.keypad_pound);
		keypadPound.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadPassword += "#";
				if (!mute) { mpDigitBeep.start(); }
			}
		});
		Button keypadContinue = (Button) findViewById(R.id.keypad_continue);
		keypadContinue.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				keypadTryPassword(keypadPassword);
			}
		});
		ImageView keypadSwitch = (ImageView) findViewById(R.id.switch_button);
		keypadSwitch.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
					switchView();
			}
		});
	}
    
	private void keypadOnDestroy() {
		if (mpDigitBeep!=null) {
			mpDigitBeep.release();
			mpErrorBeep.release();
			mpSuccessBeep.release();
			mpDigitBeep=null;
			mpErrorBeep=null;
			mpSuccessBeep=null;
		}
	}
	
	private void keypadTryPassword(String password) {
		if (checkUserPassword(password)){
			if (debug) Log.d(TAG,"match!!");
			if (!mute) {
				mpSuccessBeep.start();
			}
			gotPassword();
		}else{
			if (debug) Log.d(TAG,"bad password");
			if (!mute) {
				mpErrorBeep.start();
			}
		    Animation shake = AnimationUtils
	        	.loadAnimation(AskPassword.this, R.anim.shake);
	        findViewById(R.id.keypad_continue).startAnimation(shake);

	        keypadPassword="";
		}
	}
}