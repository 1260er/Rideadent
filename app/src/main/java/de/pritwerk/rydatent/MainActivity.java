package de.pritwerk.rydatent;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class MainActivity extends Activity {
    private enum Page {
        HOME,
        SETTINGS,
        DEVICES,
        AUTOMATIONS,
        PERMISSIONS,
        DIAGNOSTICS,
        ABOUT
    }

    private Page currentPage = Page.HOME;
    private LinearLayout content;
    private TextView pageTitle;
    private boolean firstResume = true;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTask = new Runnable() {
        @Override public void run() {
            if (currentPage == Page.HOME && homeRuntime != null && !isFinishing()) {
                updateHomeStatus();
                statusHandler.postDelayed(this, 15000L);
            }
        }
    };
    private final LinearLayout[] navIndicators = new LinearLayout[3];
    private final TextView[] navLabels = new TextView[3];
    private final ImageView[] navImages = new ImageView[3];
    private TextView homeRuntime;
    private TextView homeService;
    private TextView homeBlitzerBluetooth;
    private TextView homeCallBluetooth;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getSplashScreen().setOnExitAnimationListener(splash -> splash.remove());

        Prefs.get(this)
                .edit()
                .remove("test_number")
                .apply();

        boolean wasRuntime = Prefs.runtimeEnabled(this);

        Prefs.get(this)
                .edit()
                .putBoolean("runtime_enabled", true)
                .apply();

        draw();

        if (!wasRuntime
                && Prefs.get(this).getBoolean("blitzer_enabled", false)
                && Prefs.connected(this, "blitzer")) {
            getWindow().getDecorView().postDelayed(
                    () -> Blitzer.command(this, true),
                    50L);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (firstResume) {
            firstResume = false;
        } else if (content != null) {
            renderCurrentPage();
        }

        getWindow().getDecorView().postDelayed(
                () -> BlitzerWatchService.sync(this),
                50L);
        scheduleHomeStatus();
    }

    @Override
    protected void onPause() {
        statusHandler.removeCallbacks(statusTask);
        super.onPause();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int color(int resource) {
        return getColor(resource);
    }

    private void draw() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_app);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(
                    dp(20) + bars.left,
                    bars.top,
                    dp(20) + bars.right,
                    Math.max(dp(16), bars.bottom));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        pageTitle = new TextView(this);
        pageTitle.setTextSize(28);
        pageTitle.setTextColor(color(R.color.ui_text_primary));
        pageTitle.setTypeface(pageTitle.getTypeface(), Typeface.BOLD);
        header.addView(pageTitle,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_rydatent_settings);
        settings.setImageTintList(ColorStateList.valueOf(color(R.color.ui_text_primary)));
        settings.setBackgroundResource(android.R.drawable.list_selector_background);
        settings.setContentDescription(getString(R.string.page_settings));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settings.setOnClickListener(view -> navigate(Page.SETTINGS));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(14));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        nav.setBackgroundResource(R.drawable.nav_bar_background);
        addNavItem(nav, 0, R.drawable.ic_rydatent_home, R.string.page_home, Page.HOME);
        addNavItem(nav, 1, R.drawable.ic_rydatent_settings, R.string.page_settings, Page.SETTINGS);
        addNavItem(nav, 2, R.drawable.ic_rydatent_info, R.string.page_about, Page.ABOUT);
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

        setContentView(root);
        root.requestApplyInsets();
        renderCurrentPage();
    }

    private void addNavItem(LinearLayout parent, int index, int icon, int label, Page page) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setContentDescription(getString(label));

        LinearLayout indicator = new LinearLayout(this);
        indicator.setGravity(Gravity.CENTER);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        indicator.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));
        item.addView(indicator, new LinearLayout.LayoutParams(dp(72), dp(34)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(12);
        LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        caption.topMargin = dp(2);
        item.addView(text, caption);

        parent.addView(item, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        navIndicators[index] = indicator;
        navLabels[index] = text;
        navImages[index] = image;
        item.setOnClickListener(view -> navigate(page));
    }

    private void updateNavigation() {
        int active = currentPage == Page.HOME ? 0 : currentPage == Page.ABOUT ? 2 : 1;
        for (int i = 0; i < navLabels.length; i++) {
            boolean selected = i == active;
            int tint = color(selected ? R.color.ui_control_accent : R.color.ui_text_secondary);
            navLabels[i].setTextColor(tint);
            navLabels[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            navImages[i].setImageTintList(ColorStateList.valueOf(tint));
            navIndicators[i].setBackgroundResource(
                    selected ? R.drawable.nav_active_background : 0);
        }
    }

    private void navigate(Page page) {
        currentPage = page;
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        statusHandler.removeCallbacks(statusTask);
        homeRuntime = null;
        homeService = null;
        homeBlitzerBluetooth = null;
        homeCallBluetooth = null;
        content.removeAllViews();

        switch (currentPage) {
            case SETTINGS:
                pageTitle.setText(R.string.page_settings);
                renderSettings();
                break;
            case DEVICES:
                pageTitle.setText(R.string.page_devices);
                renderDevices();
                break;
            case AUTOMATIONS:
                pageTitle.setText(R.string.page_automations);
                renderAutomations();
                break;
            case PERMISSIONS:
                pageTitle.setText(R.string.page_permissions);
                renderPermissions();
                break;
            case DIAGNOSTICS:
                pageTitle.setText(R.string.page_diagnostics);
                renderDiagnostics();
                break;
            case ABOUT:
                pageTitle.setText(R.string.page_about);
                renderAbout();
                break;
            case HOME:
            default:
                pageTitle.setText(R.string.app_name);
                renderHome();
                break;
        }
        updateNavigation();
    }

    private void renderSettings() {
        LinearLayout settings = card();
        title(settings, R.string.page_settings);
        secondary(settings, getString(R.string.settings_intro));
        action(settings, R.string.page_devices, () -> navigate(Page.DEVICES));
        action(settings, R.string.page_automations, () -> navigate(Page.AUTOMATIONS));
        action(settings, R.string.page_permissions, () -> navigate(Page.PERMISSIONS));
        action(settings, R.string.page_diagnostics, () -> navigate(Page.DIAGNOSTICS));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundResource(R.drawable.card_background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);

        content.addView(card, params);
        return card;
    }

    private TextView title(LinearLayout parent, int stringResource) {
        TextView view = new TextView(this);
        view.setText(stringResource);
        view.setTextSize(18);
        view.setTextColor(color(R.color.ui_text_primary));
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setPadding(0, 0, 0, dp(8));
        parent.addView(view);
        return view;
    }

    private TextView body(LinearLayout parent, CharSequence text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(color(R.color.ui_text_primary));
        view.setLineSpacing(0f, 1.08f);
        parent.addView(view);
        return view;
    }

    private TextView secondary(LinearLayout parent, CharSequence text) {
        TextView view = body(parent, text);
        view.setTextColor(color(R.color.ui_text_secondary));
        return view;
    }

    private Button action(LinearLayout parent, int textResource, Runnable action) {
        Button button = new Button(this);
        button.setText(textResource);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(color(R.color.ui_text_primary));
        button.setBackgroundResource(R.drawable.button_background);
        button.setMinHeight(dp(52));
        button.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);

        parent.addView(button, params);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private CheckBox checkbox(LinearLayout parent, int textResource, String key) {
        CheckBox checkbox = new CheckBox(this);
        checkbox.setText(textResource);
        checkbox.setTextSize(16);
        checkbox.setTextColor(color(R.color.ui_text_primary));
        checkbox.setPadding(0, dp(4), 0, dp(4));
        checkbox.setChecked(Prefs.get(this).getBoolean(key, false));

        parent.addView(checkbox);

        checkbox.setOnCheckedChangeListener((button, checked) -> {
            Prefs.get(this)
                    .edit()
                    .putBoolean(key, checked)
                    .apply();

            if ("blitzer_enabled".equals(key) || "reply_enabled".equals(key)) {
                BlitzerWatchService.sync(this);
            }

            if (currentPage == Page.HOME) {
                renderCurrentPage();
            }
        });

        return checkbox;
    }

    private void renderHome() {
        LinearLayout hero = card();
        hero.setBackgroundResource(R.drawable.home_hero_background);
        hero.setPadding(dp(10), dp(14), dp(14), dp(14));
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setOrientation(LinearLayout.HORIZONTAL);

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.rydatent_hero_logo);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(mark, new LinearLayout.LayoutParams(dp(132), dp(142)));

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams heroTextParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        heroTextParams.leftMargin = dp(8);
        hero.addView(heroText, heroTextParams);
        TextView name = body(heroText, getString(R.string.app_name));
        name.setTextSize(27);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(0xFFFFFFFF);
        TextView subtitle = body(heroText, getString(R.string.home_hero_title));
        subtitle.setTextSize(19);
        subtitle.setTextColor(0xFFE2E0DE);
        subtitle.setPadding(0, dp(8), 0, 0);

        homeAutomationCard(R.drawable.ic_rydatent_camera,
                R.string.home_blitzer_title, R.string.home_blitzer_hint, "blitzer_enabled");
        homeAutomationCard(R.drawable.ic_rydatent_phone,
                R.string.device_calls, R.string.home_calls_hint, "reply_enabled");

        LinearLayout status = card();
        title(status, R.string.home_status);
        homeRuntime = homeStatusLine(status);
        homeService = homeStatusLine(status);
        homeBlitzerBluetooth = homeStatusLine(status);
        homeCallBluetooth = homeStatusLine(status);
        scheduleHomeStatus();
    }

    private TextView homeStatusLine(LinearLayout parent) {
        TextView line = body(parent, "");
        line.setTextSize(15);
        line.setPadding(0, dp(7), 0, dp(7));
        return line;
    }

    private void updateHomeStatus() {
        if (homeRuntime == null || currentPage != Page.HOME) {
            return;
        }
        homeRuntime.setText(getString(R.string.status_runtime,
                getString(Prefs.runtimeEnabled(this) ? R.string.state_active : R.string.state_inactive)));
        homeService.setText(getString(R.string.status_service,
                ServiceState.label(this, driveServiceWanted())));
        homeBlitzerBluetooth.setText(getString(R.string.status_blitzer_bt,
                getString(Prefs.connected(this, "blitzer")
                        ? R.string.state_connected : R.string.state_disconnected)));
        homeCallBluetooth.setText(getString(R.string.status_call_bt,
                getString(Prefs.connected(this, "sms")
                        ? R.string.state_connected : R.string.state_disconnected)));
    }

    private void scheduleHomeStatus() {
        statusHandler.removeCallbacks(statusTask);
        if (currentPage == Page.HOME && homeRuntime != null && !isFinishing()) {
            updateHomeStatus();
            statusHandler.postDelayed(statusTask, 15000L);
        }
    }

    private void homeAutomationCard(int iconResource, int titleResource,
                                    int hintResource, String preferenceKey) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        ImageView badge = new ImageView(this);
        badge.setImageResource(iconResource);
        badge.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        badge.setPadding(dp(12), dp(12), dp(12), dp(12));
        badge.setBackgroundResource(R.drawable.home_icon_badge);
        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(dp(52), dp(52));
        badgeParams.rightMargin = dp(14);
        card.addView(badge, badgeParams);

        LinearLayout descriptions = new LinearLayout(this);
        descriptions.setOrientation(LinearLayout.VERTICAL);
        card.addView(descriptions, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView heading = body(descriptions, getString(titleResource));
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextSize(17);
        TextView state = body(descriptions, "");
        state.setTextSize(15);
        state.setPadding(0, dp(4), 0, dp(4));
        TextView hint = secondary(descriptions, getString(hintResource));
        hint.setTextSize(13);

        Switch control = new Switch(this);
        control.setContentDescription(getString(titleResource));
        control.setShowText(false);
        int[][] states = new int[][] { { android.R.attr.state_checked }, {} };
        control.setThumbTintList(new ColorStateList(states,
                new int[] { 0xFFFFFFFF, color(R.color.ui_switch_thumb_off) }));
        control.setTrackTintList(new ColorStateList(states,
                new int[] { color(R.color.ui_control_accent), color(R.color.ui_switch_track_off) }));
        LinearLayout.LayoutParams controlParams =
                new LinearLayout.LayoutParams(dp(58), dp(52));
        controlParams.leftMargin = dp(4);
        card.addView(control, controlParams);

        boolean enabled = Prefs.get(this).getBoolean(preferenceKey, false);
        control.setChecked(enabled);
        updateHomeSwitchState(state, enabled);
        control.setOnCheckedChangeListener((button, checked) -> {
            Prefs.get(this).edit().putBoolean(preferenceKey, checked).apply();
            BlitzerWatchService.sync(this);
            updateHomeSwitchState(state, checked);
            updateHomeStatus();
        });
    }

    private void updateHomeSwitchState(TextView state, boolean active) {
        state.setText(active ? R.string.home_switch_on : R.string.home_switch_off);
        state.setTextColor(color(active ? R.color.ui_control_accent : R.color.ui_text_secondary));
    }

    private void renderDevices() {
        LinearLayout intro = card();
        secondary(intro, getString(R.string.devices_intro));

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            LinearLayout missing = card();
            secondary(missing, getString(R.string.devices_permission_missing));
            action(missing, R.string.permission_request_runtime, this::requestRuntimePermissions);
            return;
        }

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        Set<BluetoothDevice> devices = adapter == null ? Set.of() : adapter.getBondedDevices();

        if (devices.isEmpty()) {
            LinearLayout empty = card();
            secondary(empty, getString(R.string.devices_none));
            return;
        }

        for (BluetoothDevice device : devices) {
            LinearLayout deviceCard = card();

            String name = device.getName();
            if (name == null || name.isBlank()) {
                name = device.getAddress();
            }

            TextView deviceTitle = body(deviceCard, name);
            deviceTitle.setTypeface(deviceTitle.getTypeface(), Typeface.BOLD);

            secondary(deviceCard, device.getAddress());

            deviceChoice(deviceCard, R.string.device_blitzer, "blitzer", device.getAddress());
            deviceChoice(deviceCard, R.string.device_calls, "sms", device.getAddress());
        }
    }

    private void deviceChoice(LinearLayout parent, int labelResource, String type, String address) {
        CheckBox checkbox = new CheckBox(this);
        checkbox.setText(labelResource);
        checkbox.setTextSize(16);
        checkbox.setTextColor(color(R.color.ui_text_primary));
        checkbox.setChecked(Prefs.chosen(this, type).contains(address));

        parent.addView(checkbox);

        checkbox.setOnCheckedChangeListener((button, checked) -> {
            Set<String> selected = Prefs.chosen(this, type);

            if (checked) {
                selected.add(address);
            } else {
                selected.remove(address);
            }

            Prefs.get(this)
                    .edit()
                    .putStringSet("choose_" + type, selected)
                    .apply();

            BlitzerWatchService.sync(this);
        });
    }

    private void renderAutomations() {
        LinearLayout blitzer = card();
        title(blitzer, R.string.device_blitzer);
        checkbox(blitzer, R.string.automation_blitzer, "blitzer_enabled");
        secondary(blitzer, getString(R.string.automation_blitzer_hint));
        action(blitzer, R.string.action_start_blitzer, () -> Blitzer.command(this, true));
        action(blitzer, R.string.action_stop_blitzer, () -> Blitzer.command(this, false));

        LinearLayout calls = card();
        title(calls, R.string.device_calls);
        checkbox(calls, R.string.automation_calls, "reply_enabled");
        secondary(calls, getString(R.string.automation_calls_hint));

        secondary(calls, getString(R.string.sms_text_label));

        EditText smsText = new EditText(this);
        smsText.setText(Prefs.smsText(this));
        smsText.setTextSize(16);
        smsText.setTextColor(color(R.color.ui_text_primary));
        smsText.setMinLines(2);
        smsText.setMaxLines(4);
        smsText.setSingleLine(false);
        smsText.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout.LayoutParams smsTextParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        smsTextParams.topMargin = dp(8);

        calls.addView(smsText, smsTextParams);

        action(calls, R.string.action_save_sms_text, () -> {
            boolean saved =
                    Prefs.setSmsText(
                            this,
                            smsText.getText().toString());

            if (saved) {
                Toast.makeText(
                        this,
                        R.string.toast_sms_text_saved,
                        Toast.LENGTH_SHORT)
                        .show();
            } else {
                smsText.setText(Prefs.smsText(this));

                Toast.makeText(
                        this,
                        R.string.toast_sms_text_required,
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });

        LinearLayout startup = card();
        title(startup, R.string.automation_autostart);
        checkbox(startup, R.string.automation_autostart, "auto_start");
        secondary(startup, getString(R.string.automation_autostart_hint));
    }

    private void renderPermissions() {
        LinearLayout runtime = card();
        title(runtime, R.string.permission_runtime_title);

        status(runtime, R.string.permission_bluetooth,
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED);

        status(runtime, R.string.permission_contacts,
                checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        == PackageManager.PERMISSION_GRANTED);

        status(runtime, R.string.permission_sms,
                checkSelfPermission(Manifest.permission.SEND_SMS)
                        == PackageManager.PERMISSION_GRANTED);

        status(runtime, R.string.permission_notifications,
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED);

        action(runtime, R.string.permission_request_runtime, this::requestRuntimePermissions);

        action(runtime, R.string.permission_notification_post, () -> requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                11));

        LinearLayout special = card();

        RoleManager role = getSystemService(RoleManager.class);
        boolean callRole = role != null && role.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);

        status(special, R.string.permission_call_screening, callRole);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        boolean listenerAccess = notificationManager != null
                && notificationManager.isNotificationListenerAccessGranted(
                new ComponentName(this, BlitzerListener.class));

        status(special, R.string.permission_notification_listener, listenerAccess);

        action(special, R.string.permission_call_role, this::requestCallRole);

        action(special, R.string.permission_notification_access,
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        LinearLayout system = card();

        ActivityManager activityManager = getSystemService(ActivityManager.class);
        PowerManager powerManager = getSystemService(PowerManager.class);

        boolean restricted = activityManager != null && activityManager.isBackgroundRestricted();
        boolean exempt = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(getPackageName());

        body(system, getString(
                R.string.status_line,
                getString(R.string.permission_background),
                getString(activityManager == null
                        ? R.string.state_unknown
                        : restricted
                        ? R.string.state_restricted
                        : R.string.state_unrestricted)));

        body(system, getString(
                R.string.status_line,
                getString(R.string.permission_battery),
                getString(powerManager == null
                        ? R.string.state_unknown
                        : exempt
                        ? R.string.state_exempt
                        : R.string.state_optimized)));

        action(system, R.string.permission_app_settings, this::openAppSettings);
    }

    private void status(LinearLayout parent, int label, boolean granted) {
        body(parent, getString(
                R.string.status_line,
                getString(label),
                getString(granted ? R.string.state_granted : R.string.state_missing)));
    }

    private void renderDiagnostics() {
        LinearLayout card = card();
        secondary(card, getString(R.string.diagnostics_intro));

        String history = Prefs.get(this).getString("event_history", "");

        body(card, history.isBlank()
                ? getString(R.string.diagnostics_empty)
                : history);

        action(card, R.string.action_clear_log, () -> {
            Prefs.clearHistory(this);
            renderCurrentPage();
        });
    }

    private void renderAbout() {
        LinearLayout about = card();
        title(about, R.string.about_title);
        body(about, getString(R.string.about_version, versionName()));
        secondary(about, getString(R.string.about_text));

        LinearLayout privacy = card();
        secondary(privacy, getString(R.string.about_privacy));
    }

    private String versionName() {
        try {
            String version = getPackageManager()
                    .getPackageInfo(
                            getPackageName(),
                            PackageManager.PackageInfoFlags.of(0))
                    .versionName;

            return version == null ? "?" : version;
        } catch (PackageManager.NameNotFoundException exception) {
            return "?";
        }
    }

    private void requestRuntimePermissions() {
        requestPermissions(
                new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.SEND_SMS
                },
                10);
    }

    private void requestCallRole() {
        RoleManager role = getSystemService(RoleManager.class);

        if (role != null && role.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, R.string.toast_call_role_active, Toast.LENGTH_SHORT).show();
            return;
        }

        if (role != null && role.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(
                    role.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                    22);
            return;
        }

        Toast.makeText(this, R.string.toast_call_role_unavailable, Toast.LENGTH_LONG).show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));

        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.toast_app_settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private boolean driveServiceWanted() {
        return Logic.shouldRunDriveService(
                Prefs.runtimeEnabled(this),
                Prefs.get(this).getBoolean("blitzer_enabled", false),
                Prefs.connected(this, "blitzer"),
                Prefs.get(this).getBoolean("reply_enabled", false),
                Prefs.connected(this, "sms"));
    }
}
