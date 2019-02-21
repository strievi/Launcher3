package com.google.android.apps.nexuslauncher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;

public class SettingsActivity extends com.android.launcher3.SettingsActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {
    public final static String ICON_PACK_PREF = "pref_icon_pack";
    public final static String APP_INFO_PREF = "app_info";
    public final static String SHOW_PREDICTIONS_PREF = "pref_show_predictions";
    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";
    public final static String SMARTSPACE_PREF = "pref_smartspace";
    public final static String APP_VERSION_PREF = "about_app_version";
    private final static String BRIDGE_TAG = "tag_bridge";
    private final static String GOOGLE_APP = "com.google.android.googlequicksearchbox";

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new MySettingsFragment()).commit();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment, Preference preference) {
        Fragment instantiate = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        if (instantiate instanceof DialogFragment) {
            ((DialogFragment) instantiate).show(getFragmentManager(), preference.getKey());
        } else {
            getFragmentManager().beginTransaction().replace(android.R.id.content, instantiate).addToBackStack(preference.getKey()).commit();
        }
        return true;
    }

    public static class MySettingsFragment extends com.android.launcher3.SettingsActivity.LauncherSettingsFragment
            implements Preference.OnPreferenceChangeListener, AdapterView.OnItemLongClickListener {
        private CustomIconPreference mIconPackPref;
        private Context mContext;

        @Override
        public void onCreate(Bundle bundle) {
            super.onCreate(bundle);

            mContext = getActivity();

            findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);
            findPreference(ENABLE_MINUS_ONE_PREF).setOnPreferenceChangeListener(this);
            findPreference(ENABLE_MINUS_ONE_PREF).setTitle(getDisplayGoogleTitle());

            PackageManager packageManager = mContext.getPackageManager();
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);
                findPreference(APP_VERSION_PREF).setSummary(packageInfo.versionName);
            } catch (PackageManager.NameNotFoundException ex) {
                Log.e("SettingsActivity", "Unable to load my own package info", ex);
            }

            try {
                ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(GOOGLE_APP, 0);
                if (!applicationInfo.enabled) {
                    throw new PackageManager.NameNotFoundException();
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                getPreferenceScreen().removePreference(findPreference(SettingsActivity.ENABLE_MINUS_ONE_PREF));
            }

            mIconPackPref = (CustomIconPreference) findPreference(ICON_PACK_PREF);
            mIconPackPref.setOnPreferenceChangeListener(this);

            findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);

            Preference appInfoPref = findPreference(APP_INFO_PREF);
            Uri packageUri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
            Intent appInfoPrefIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appInfoPrefIntent.setData(packageUri);
            appInfoPref.setIntent(appInfoPrefIntent);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View result = super.onCreateView(inflater, container, savedInstanceState);
            if (FeatureFlags.ALWAYS_ALLOW_HIDING && result != null) {
                View lv = result.findViewById(android.R.id.list);
                if (lv instanceof ListView) {
                    ((ListView) lv).setOnItemLongClickListener(this);
                }
            }
            return result;
        }

        private String getDisplayGoogleTitle() {
            CharSequence charSequence = null;
            try {
                Resources resourcesForApplication = mContext.getPackageManager().getResourcesForApplication(GOOGLE_APP);
                int identifier = resourcesForApplication.getIdentifier("title_google_home_screen", "string", GOOGLE_APP);
                if (identifier != 0) {
                    charSequence = resourcesForApplication.getString(identifier);
                }
            }
            catch (PackageManager.NameNotFoundException ex) {
            }
            if (TextUtils.isEmpty(charSequence)) {
                charSequence = mContext.getString(R.string.title_google_app);
            }
            return mContext.getString(R.string.title_show_google_app, charSequence);
        }

        @Override
        public void onResume() {
            super.onResume();
            mIconPackPref.reloadIconPacks();

            SwitchPreference minusOne = (SwitchPreference) findPreference(ENABLE_MINUS_ONE_PREF);
            if (minusOne != null && !PixelBridge.isInstalled(getActivity())) {
                minusOne.setChecked(false);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, final Object newValue) {
            switch (preference.getKey()) {
                case ENABLE_MINUS_ONE_PREF:
                    if (PixelBridge.isInstalled(getActivity())) {
                        return true;
                    }
                    FragmentManager fm = getFragmentManager();
                    if (fm.findFragmentByTag(BRIDGE_TAG) == null) {
                        InstallFragment fragment = new InstallFragment();
                        fragment.show(fm, BRIDGE_TAG);
                    }
                    break;
                case ICON_PACK_PREF:
                    if (!CustomIconUtils.getCurrentPack(mContext).equals(newValue)) {
                        final ProgressDialog applyingDialog = ProgressDialog.show(mContext,
                                null /* title */,
                                mContext.getString(R.string.state_loading),
                                true /* indeterminate */,
                                false /* cancelable */);

                        CustomIconUtils.setCurrentPack(getActivity(), (String) newValue);
                        CustomIconUtils.applyIconPackAsync(mContext);

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                applyingDialog.cancel();
                            }
                        }, 1000);
                    }
                    return true;
                case SHOW_PREDICTIONS_PREF:
                    if ((boolean) newValue) {
                        return true;
                    }
                    SettingsActivity.SuggestionConfirmationFragment confirmationFragment = new SettingsActivity.SuggestionConfirmationFragment();
                    confirmationFragment.setTargetFragment(this, 0);
                    confirmationFragment.show(getFragmentManager(), preference.getKey());
                    break;
            }
            return false;
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
            ListView listView = (ListView) parent;
            ListAdapter listAdapter = listView.getAdapter();
            Object obj = listAdapter.getItem(pos);
            if (obj != null && obj instanceof View.OnLongClickListener) {
                View.OnLongClickListener longListener = (View.OnLongClickListener) obj;
                return longListener.onLongClick(view);
            } else {
                Preference preference = (Preference) obj;
                switch (preference.getKey()) {
                    case ICON_PACK_PREF:
                        SettingsActivity.ResetConfirmationFragment confirmationFragment = new SettingsActivity.ResetConfirmationFragment();
                        confirmationFragment.setTargetFragment(this, 0);
                        confirmationFragment.show(getFragmentManager(), preference.getKey());
                        return true;
                }
            }
            return false;
        }
    }

    public static class OpenSourceLicensesFragment extends DialogFragment {
        public Dialog onCreateDialog(Bundle bundle) {
            WebView view = new WebView(getActivity());
            view.setWebViewClient(new WebViewClient());
            view.getSettings().setBuiltInZoomControls(true);
            view.loadUrl("file:///android_asset/license.html");
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.pref_open_source_licenses_title)
                    .setView(view)
                    .create();
        }
    }

    public static class SuggestionConfirmationFragment extends DialogFragment implements DialogInterface.OnClickListener {
        public void onClick(final DialogInterface dialogInterface, final int n) {
            if (getTargetFragment() instanceof PreferenceFragment) {
                Preference preference = ((PreferenceFragment) getTargetFragment()).findPreference(SHOW_PREDICTIONS_PREF);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setChecked(false);
                }
            }
        }

        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_disable_suggestions_prompt)
                    .setMessage(R.string.msg_disable_suggestions_prompt)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.label_turn_off_suggestions, this).create();
        }
    }

    public static class ResetConfirmationFragment extends DialogFragment implements DialogInterface.OnClickListener {
        public void onClick(final DialogInterface dialogInterface, final int n) {
            Context context = getActivity();
            CustomIconUtils.unhideAllApps(context);
        }

        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_reset_hidden_apps_prompt)
                    .setMessage(R.string.msg_reset_hidden_apps_prompt)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.label_reset_hidden_apps, this).create();
        }
    }

    public static class InstallFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.bridge_missing_title)
                    .setMessage(R.string.bridge_missing_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
        }
    }
}
