package com.isecpartners.android.manifestexplorer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Tool for exploring the configuration of proprietary Android builds. Also
 * useful for validating the configuration of non-proprietary builds. Originally
 * wrote this for analyzing the pre-open source versions of Android that were
 * shipping only in the SDK. These versions didn't expose the critical
 * AndroidManifest.xml system file, but this tool lets you see that information
 * as the system stores it for use at runtime and makes it generally available
 * to processes. Other proprietary builds of Android may also not provide this
 * system file, which defines much of the security policy of the platform, and
 * is needed for system hardening and to understand what protections of user
 * data are in place.
 * 
 * @author Jesse (c) 2008-2009, iSEC Partners
 * 
 */
public class ManifestExplorer extends Activity {
	Button mRun = null;
	Button mSave = null;
	EditText mInput = null;
	TextView mOut = null;
	Spinner mSpin = null;

	String pkgName = null;
	String[] mPkgs = null;

	// current resolution context configured per package, defaults to self
	AssetManager mCurAm = null;
	Resources mCurResources = null;

	public static final String MANIFEST_TAG = "Manifest Explorer";
	public static final String EXTRA_PACKAGE_NAME = "PackageToView";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		ArrayAdapter<String> spinnerArrayAdapter = null;
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.main);
		this.initControls();

		// setup packages list for our spinner
		mPkgs = this.getPackages();
		spinnerArrayAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_dropdown_item, mPkgs);
		this.mSpin.setAdapter(spinnerArrayAdapter);

		if (this.getIntent() != null) {
			String name = this.getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
			if (name != null) {
				Log.d(MANIFEST_TAG, "started for pkg: " + name);
				int pos = spinnerArrayAdapter.getPosition(name);
				if (pos > -1)
					this.mSpin.setSelection(pos);
			}
		}

		this.setPkgName(mSpin.getSelectedItem().toString());
		configForPackage(this.getPkgName());
		this.updateView();
	}

	// Menu definition
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, 1, Menu.NONE, "Show / Hide Save");
		return true;
	}

	// Menu handling
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mInput.getVisibility() == View.GONE) {
			mInput.setVisibility(View.VISIBLE);
			mSave.setVisibility(View.VISIBLE);
		} else {
			mInput.setVisibility(View.GONE);
			mSave.setVisibility(View.GONE);
		}
		return true;
	}

	protected String[] getPackages() {
		ArrayList<String> res = new ArrayList<String>();
		List<PackageInfo> l = getPackageManager().getInstalledPackages(
				0xFFFFFFFF);
		for (PackageInfo pi : l)
			res.add(pi.packageName);
		return res.toArray(new String[res.size()]);
	}

	protected void initControls() {
		this.mRun = (Button) this.findViewById(R.id.run);
		this.mSave = (Button) this.findViewById(R.id.save);
		this.mOut = (TextView) this.findViewById(R.id.output);
		this.mInput = (EditText) this.findViewById(R.id.filename);
		this.mSpin = (Spinner) this.findViewById(R.id.toDump);

		mRun.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				setPkgName(mSpin.getSelectedItem().toString());
				configForPackage(getPkgName());
				updateView();
			}
		});

		mSave.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					FileOutputStream fos;
					fos = new FileOutputStream(mInput.getText().toString());
					fos.write(mOut.getText().toString().getBytes());
					fos.close();
					Toast.makeText(ManifestExplorer.this, "File saved",
							Toast.LENGTH_SHORT).show();
				} catch (IOException ioe) {
					showError("Saving file " + mInput.getText().toString(), ioe);
				}
			}
		});
	}

	protected boolean configForPackage(String packageName) {
		if (packageName == null || packageName == "")
			packageName = "android";
		AssetManager initAM = mCurAm;
		Resources initRes = mCurResources;
		try {
			mCurAm = createPackageContext(packageName, 0).getAssets();
			mCurResources = new Resources(mCurAm, getResources()
					.getDisplayMetrics(), null);
		} catch (NameNotFoundException name) {
			Toast.makeText(this, "Error, couldn't create package context: "
					+ name.getLocalizedMessage(), Toast.LENGTH_LONG);
			mCurAm = initAM;
			mCurResources = initRes;
			return false;
		} catch (RuntimeException unexpected) {
			Log.e(MANIFEST_TAG, "error configuring for package: " + packageName
					+ " " + unexpected.getMessage());
			mCurAm = initAM;
			mCurResources = initRes;
			return false;
		}
		return true;
	}

	protected void updateView() {
		this.mOut.setText("");
		try {
			XmlResourceParser xml = null;
			xml = mCurAm.openXmlResourceParser("AndroidManifest.xml");
			this.mInput.setText("/sdcard/" + getPkgName() + ".txt");
			this.mOut.append(getXMLText(xml, mCurResources));
		} catch (IOException ioe) {
			this.showError("Reading XML", ioe);
		}
	}

	protected void showError(CharSequence text, Throwable t) {
		Log.e("ManifestExplorer", text + " : "
				+ ((t != null) ? t.getMessage() : ""));
		Toast.makeText(this, "Error: " + text + " : " + t, Toast.LENGTH_LONG)
				.show();
	}

	protected void insertSpaces(StringBuffer sb, int num) {
		if (sb == null)
			return;
		for (int i = 0; i < num; i++)
			sb.append(" ");
	}

	protected CharSequence getXMLText(XmlResourceParser xrp,
			Resources currentResources) {
		StringBuffer sb = new StringBuffer();
		int indent = 0;
		try {
			int eventType = xrp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				// for sb
				switch (eventType) {
				case XmlPullParser.START_TAG:
					indent += 1;
					sb.append("\n");
					insertSpaces(sb, indent);
					sb.append("<" + xrp.getName());
					sb.append(getAttribs(xrp, currentResources));
					sb.append(">");
					break;
				case XmlPullParser.END_TAG:
					indent -= 1;
					sb.append("\n");
					insertSpaces(sb, indent);
					sb.append("</" + xrp.getName() + ">");
					break;

				case XmlPullParser.TEXT:
					sb.append("" + xrp.getText());
					break;

				case XmlPullParser.CDSECT:
					sb.append("<!CDATA[" + xrp.getText() + "]]>");
					break;

				case XmlPullParser.PROCESSING_INSTRUCTION:
					sb.append("<?" + xrp.getText() + "?>");
					break;

				case XmlPullParser.COMMENT:
					sb.append("<!--" + xrp.getText() + "-->");
					break;
				}
				eventType = xrp.nextToken();
			}
		} catch (IOException ioe) {
			showError("Reading XML", ioe);
		} catch (XmlPullParserException xppe) {
			showError("Parsing XML", xppe);
		}
		return sb;
	}

	/**
	 * returns the value, resolving it through the provided resources if it
	 * appears to be a resource ID. Otherwise just returns what was provided.
	 * 
	 * @param in
	 *            String to resolve
	 * @param r
	 *            Context appropriate resource (system for system, package's for
	 *            package)
	 * @return Resolved value, either the input, or some other string.
	 */
	private String resolveValue(String in, Resources r) {
		if (in == null || !in.startsWith("@") || r == null)
			return in;
		try {
			int num = Integer.parseInt(in.substring(1));
			return r.getString(num);
		} catch (NumberFormatException e) {
			return in;
		} catch (RuntimeException e) {
			// formerly noted errors here, but simply not resolving works better
			return in;
		}
	}

	private CharSequence getAttribs(XmlResourceParser xrp,
			Resources currentResources) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < xrp.getAttributeCount(); i++)
			sb.append("\n" + xrp.getAttributeName(i) + "=\""
					+ resolveValue(xrp.getAttributeValue(i), currentResources)
					+ "\"");
		return sb;
	}

	public String getPkgName() {
		return this.pkgName;
	}

	public void setPkgName(String pkgName) {
		this.pkgName = pkgName;
	}

}