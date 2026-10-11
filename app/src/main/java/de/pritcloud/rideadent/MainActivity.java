package de.pritcloud.rideadent;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.app.role.RoleManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.WindowInsets;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private enum Page {
        HOME,
        SETTINGS,
        DEVICES,
        AUTOMATIONS,
        PERMISSIONS,
        BATTERY,
        DISPLAY,
        DIAGNOSTICS,
        ABOUT
    }

    private Page currentPage = Page.HOME;
    private final ArrayDeque<Page> pageHistory = new ArrayDeque<>();
    private final OnBackInvokedCallback backGestureCallback = this::goBack;
    private ImageButton backButton;
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
    private LinearLayout homeBlitzerBluetooth;
    private LinearLayout homeCallBluetooth;
    private TextView homePermissions;
    private TextView homeBattery;

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

        if (state != null) {
            String restoredPage = state.getString("rideadent_current_page");
            if (restoredPage != null) {
                try { currentPage = Page.valueOf(restoredPage); }
                catch (IllegalArgumentException ignored) { currentPage = Page.HOME; }
            }
            ArrayList<String> savedHistory = state.getStringArrayList("rideadent_page_history");
            if (savedHistory != null) {
                for (String name : savedHistory) {
                    try { pageHistory.addLast(Page.valueOf(name)); }
                    catch (IllegalArgumentException ignored) { /* skip obsolete page */ }
                }
            }
        }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backGestureCallback);
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

    @Override
    protected void onDestroy() {
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backGestureCallback);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("rideadent_current_page", currentPage.name());
        ArrayList<String> names = new ArrayList<>();
        for (Page page : pageHistory) names.add(page.name());
        state.putStringArrayList("rideadent_page_history", names);
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

        backButton = new ImageButton(this);
        backButton.setImageResource(R.drawable.ic_rideadent_back);
        backButton.setImageTintList(ColorStateList.valueOf(color(R.color.ui_control_accent)));
        backButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        backButton.setContentDescription(getString(R.string.action_back));
        backButton.setOnClickListener(view -> goBack());
        header.addView(backButton, new LinearLayout.LayoutParams(dp(52), dp(52)));
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
        addNavItem(nav, 0, R.drawable.ic_rideadent_home, R.string.page_home, Page.HOME);
        addNavItem(nav, 1, R.drawable.ic_rideadent_settings, R.string.page_settings, Page.SETTINGS);
        addNavItem(nav, 2, R.drawable.ic_rideadent_info, R.string.page_about, Page.ABOUT);
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
        if (page == currentPage) return;
        pageHistory.push(currentPage);
        currentPage = page;
        renderCurrentPage();
    }

    private void goBack() {
        if (pageHistory.isEmpty()) {
            finish();
            return;
        }
        currentPage = pageHistory.pop();
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        statusHandler.removeCallbacks(statusTask);
        homeRuntime = null;
        homeService = null;
        homeBlitzerBluetooth = null;
        homeCallBluetooth = null;
        homePermissions = null;
        homeBattery = null;
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
            case BATTERY:
                pageTitle.setText(R.string.page_battery);
                renderBattery();
                break;
            case DISPLAY:
                pageTitle.setText(R.string.page_display);
                renderDisplay();
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
        boolean subpage = currentPage == Page.DEVICES
                || currentPage == Page.AUTOMATIONS
                || currentPage == Page.PERMISSIONS
                || currentPage == Page.BATTERY
                || currentPage == Page.DISPLAY
                || currentPage == Page.DIAGNOSTICS;
        backButton.setVisibility(subpage ? View.VISIBLE : View.INVISIBLE);
        updateNavigation();
    }

    private void renderSettings() {
        settingsNavigationCard(R.drawable.ic_rideadent_permissions,
                R.string.page_permissions, R.string.settings_permissions_hint,
                () -> navigate(Page.PERMISSIONS));
        settingsNavigationCard(R.drawable.ic_rideadent_battery,
                R.string.page_battery, R.string.settings_battery_hint,
                () -> navigate(Page.BATTERY));
        settingsNavigationCard(R.drawable.ic_rideadent_bluetooth,
                R.string.page_devices, R.string.settings_devices_hint,
                () -> navigate(Page.DEVICES));
        settingsNavigationCard(R.drawable.ic_rideadent_automations,
                R.string.page_automations, R.string.settings_automations_hint,
                () -> navigate(Page.AUTOMATIONS));
        settingsNavigationCard(R.drawable.ic_rideadent_display,
                R.string.page_display, R.string.settings_display_hint,
                () -> navigate(Page.DISPLAY));
        settingsNavigationCard(R.drawable.ic_rideadent_diagnostics,
                R.string.page_diagnostics, R.string.settings_diagnostics_hint,
                () -> navigate(Page.DIAGNOSTICS));
    }

    private void settingsNavigationCard(int iconResource, int titleResource,
                                        int hintResource, Runnable onOpen) {
        LinearLayout item = card();
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) item.getLayoutParams();
        params.height = 0;
        params.weight = 1f;
        item.setLayoutParams(params);
        item.setMinimumHeight(dp(90));
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);

        ImageView badge = new ImageView(this);
        badge.setImageResource(iconResource);
        badge.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        badge.setPadding(dp(12), dp(12), dp(12), dp(12));
        badge.setBackgroundResource(R.drawable.home_icon_badge);
        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(dp(52), dp(52));
        badgeParams.rightMargin = dp(14);
        item.addView(badge, badgeParams);

        LinearLayout description = new LinearLayout(this);
        description.setOrientation(LinearLayout.VERTICAL);
        item.addView(description, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView heading = body(description, getString(titleResource));
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextSize(17);
        TextView hint = secondary(description, getString(hintResource));
        hint.setTextSize(13);
        hint.setPadding(0, dp(4), 0, 0);

        item.setContentDescription(getString(titleResource));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(view -> onOpen.run());
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
        mark.setImageResource(R.drawable.rideadent_hero_logo);
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

        homeAutomationCard(R.drawable.ic_rideadent_camera,
                R.string.home_blitzer_title, R.string.home_blitzer_hint, "blitzer_enabled");
        homeAutomationCard(R.drawable.ic_rideadent_call_arrow,
                R.string.device_calls, R.string.home_calls_hint, "reply_enabled");

        LinearLayout status = card();
        title(status, R.string.home_status);
        homeRuntime = homeStatusLine(status);
        homeService = homeStatusLine(status);
        homeBlitzerBluetooth = homeBluetoothBlock(status);
        homeCallBluetooth = homeBluetoothBlock(status);
        homePermissions = homeStatusLine(status);
        homeBattery = homeStatusLine(status);
        scheduleHomeStatus();
    }

    private TextView homeStatusLine(LinearLayout parent) {
        TextView line = body(parent, "");
        line.setTextSize(15);
        line.setPadding(0, dp(7), 0, dp(7));
        return line;
    }

    private LinearLayout homeBluetoothBlock(LinearLayout parent) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(7), 0, dp(7));
        parent.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return block;
    }

    private void updateHomeStatus() {
        if (homeRuntime == null || currentPage != Page.HOME) {
            return;
        }

        // This displays the boot setting, not whether the runtime latch is set.
        boolean automatic = Prefs.get(this).getBoolean("auto_start", false);
        setHomeStatusLineColor(homeRuntime, R.string.status_runtime,
                automatic ? R.string.state_automatic : R.string.state_manual,
                automatic ? R.color.ui_control_accent : R.color.ui_text_secondary);
        setHomeLink(homeRuntime, !automatic, view -> navigate(Page.AUTOMATIONS));

        // Distinguish inactive from the critical case: wanted but unresponsive.
        boolean needed = driveServiceWanted();
        boolean responding = needed && ServiceState.isFresh(this);
        setHomeStatusLineColor(homeService, R.string.status_service,
                !needed ? R.string.home_state_inactive
                        : responding ? R.string.home_state_active
                                     : R.string.home_state_not_responding,
                !needed ? R.color.ui_text_secondary
                        : responding ? R.color.ui_control_accent : R.color.ui_danger);
        setHomeLink(homeService, needed && !responding,
                view -> navigate(Page.DIAGNOSTICS));

        updateHomeBluetooth(homeBlitzerBluetooth, R.string.status_blitzer_bt, "blitzer");
        updateHomeBluetooth(homeCallBluetooth, R.string.status_call_bt, "sms");

        boolean granted = allHomePermissionsGranted();
        setHomeStatusLineColor(homePermissions, R.string.status_permissions,
                granted ? R.string.home_state_granted : R.string.home_state_missing,
                granted ? R.color.ui_control_accent : R.color.ui_danger);
        setHomeLink(homePermissions, !granted, view -> navigate(Page.PERMISSIONS));

        // "Optimiert" is the user-facing name for unrestricted background work.
        // Android itself calls the allowlisted state "battery optimization exempt".
        boolean unrestricted = isHomeBatteryUnrestricted();
        setHomeStatusLineColor(homeBattery, R.string.status_battery,
                unrestricted ? R.string.home_state_optimized : R.string.home_state_restricted,
                unrestricted ? R.color.ui_control_accent : R.color.ui_danger);
        setHomeLink(homeBattery, !unrestricted, view -> openAppSettings());
    }

    private void setHomeLink(View target, boolean enabled, View.OnClickListener onClick) {
        target.setOnClickListener(enabled ? onClick : null);
        target.setFocusable(enabled);
    }

    private boolean allHomePermissionsGranted() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        RoleManager role = getSystemService(RoleManager.class);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        return role != null && role.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                && notifications != null && notifications.areNotificationsEnabled()
                && notifications.isNotificationListenerAccessGranted(
                        new ComponentName(this, BlitzerListener.class));
    }

    private boolean isHomeBatteryUnrestricted() {
        ActivityManager activity = getSystemService(ActivityManager.class);
        PowerManager power = getSystemService(PowerManager.class);
        return activity != null && power != null
                && !activity.isBackgroundRestricted()
                && power.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void setHomeStatusLineColor(TextView line, int templateResource,
                                        int stateResource, int stateColorResource) {
        String state = getString(stateResource);
        String message = getString(templateResource, state);
        int start = message.lastIndexOf(state);
        SpannableString colored = new SpannableString(message);
        if (start >= 0) {
            colored.setSpan(new ForegroundColorSpan(color(stateColorResource)),
                    start, start + state.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        line.setText(colored);
    }

    private void updateHomeBluetooth(LinearLayout parent, int templateResource,
                                     String functionType) {
        if (parent == null) return;
        parent.removeAllViews();

        Set<String> relevant = Prefs.active(this);
        relevant.retainAll(Prefs.chosen(this, functionType));
        boolean connected = !relevant.isEmpty();
        setHomeLink(parent, !connected, view -> navigate(Page.DEVICES));

        if (!connected) {
            TextView disconnected = body(parent, "");
            disconnected.setTextSize(15);
            setHomeStatusLineColor(disconnected, templateResource,
                    R.string.home_state_disconnected, R.color.ui_text_secondary);
            return;
        }

        // Only already-connected, selected device addresses are used for display.
        List<String> names = new ArrayList<>();
        Set<BluetoothDevice> paired = Set.of();
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            BluetoothManager manager = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter != null) {
                try {
                    paired = adapter.getBondedDevices();
                } catch (SecurityException ignored) {
                    // A missing name falls back to the existing address.
                }
            }
        }

        for (String address : relevant) {
            String name = address;
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                for (BluetoothDevice device : paired) {
                    try {
                        if (address.equals(device.getAddress())) {
                            String found = device.getName();
                            if (found != null && !found.isBlank()) name = found;
                            break;
                        }
                    } catch (SecurityException ignored) {
                        // Bluetooth permission could have been removed meanwhile.
                    }
                }
            }
            names.add(name.replace('\n', ' ').replace('\r', ' ').trim());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        // The first device is on the label line. Any additional device begins
        // at the same x-position, and is ellipsized within the available width.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = body(row, getString(templateResource, ""));
        label.setTextSize(15);
        label.setPadding(0, 0, dp(4), 0);

        LinearLayout deviceNames = new LinearLayout(this);
        deviceNames.setOrientation(LinearLayout.VERTICAL);
        row.addView(deviceNames, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            TextView deviceName = body(deviceNames, name);
            deviceName.setTextSize(15);
            deviceName.setTextColor(color(R.color.ui_control_accent));
            deviceName.setSingleLine(true);
            deviceName.setEllipsize(TextUtils.TruncateAt.END);
            deviceName.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            deviceName.setPadding(0, i == 0 ? 0 : dp(3), 0, 0);
            deviceName.setContentDescription(name);
        }
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

    private void renderBattery() {
        PowerManager power = getSystemService(PowerManager.class);
        boolean unrestricted = power != null
                && power.isIgnoringBatteryOptimizations(getPackageName());
        LinearLayout battery = card();
        title(battery, R.string.battery_unrestricted_title);
        secondary(battery, getString(R.string.battery_unrestricted_explanation));
        permissionAction(battery, R.string.battery_unrestricted_action,
                unrestricted, this::requestUnrestrictedBattery);

        boolean noPause;
        try {
            noPause = getPackageManager().isAutoRevokeWhitelisted();
        } catch (SecurityException exception) {
            noPause = false;
        }
        LinearLayout unused = card();
        title(unused, R.string.battery_unused_title);
        secondary(unused, getString(R.string.battery_unused_explanation));
        permissionAction(unused, R.string.battery_unused_action,
                noPause, this::openUnusedAppSettings);
    }

    private void requestUnrestrictedBattery() {
        Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        request.setData(Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(request);
        } catch (RuntimeException exception) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (RuntimeException fallback) {
                openAppSettings();
            }
        }
    }

    private void openUnusedAppSettings() {
        Intent intent = new Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            openAppSettings();
        }
    }

    private void renderDisplay() {
        LinearLayout theme = card();
        title(theme, R.string.display_theme_title);
        secondary(theme, getString(R.string.display_theme_explanation));
        String selected = Prefs.get(this).getString("appearance_theme", "system");
        themeAction(theme, R.string.display_system, "system", selected);
        themeAction(theme, R.string.display_light, "light", selected);
        themeAction(theme, R.string.display_dark, "dark", selected);
    }

    private void themeAction(LinearLayout parent, int labelResource,
                             String value, String selected) {
        boolean checked = value.equals(selected);
        Button choice = action(parent, labelResource, () -> {
            if (value.equals(Prefs.get(this).getString("appearance_theme", "system"))) {
                Toast.makeText(this, R.string.setting_already_active,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            UiModeManager modeManager = getSystemService(UiModeManager.class);
            if (modeManager == null) return;
            int mode = "light".equals(value) ? UiModeManager.MODE_NIGHT_NO
                    : "dark".equals(value) ? UiModeManager.MODE_NIGHT_YES
                    : UiModeManager.MODE_NIGHT_AUTO;
            Prefs.get(this).edit().putString("appearance_theme", value).apply();
            modeManager.setApplicationNightMode(mode);
            renderCurrentPage();
        });
        if (checked) styleCompletedAction(choice, labelResource);
    }

    private void styleCompletedAction(Button button, int labelResource) {
        button.setText("✓ " + getString(labelResource));
        button.setBackgroundTintList(ColorStateList.valueOf(
                color(R.color.ui_control_accent)));
        button.setTextColor(0xFF202020);
    }

    private void permissionAction(LinearLayout parent, int labelResource,
                                  boolean granted, Runnable request) {
        Button control = action(parent, labelResource, () -> {
            if (granted) {
                Toast.makeText(this, R.string.setting_already_active,
                        Toast.LENGTH_SHORT).show();
            } else {
                request.run();
            }
        });
        if (granted) styleCompletedAction(control, labelResource);
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

        boolean corePermissions = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
        permissionAction(runtime, R.string.permission_request_runtime,
                corePermissions, this::requestRuntimePermissions);

        permissionAction(runtime, R.string.permission_notification_post,
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED,
                () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11));

        LinearLayout special = card();

        RoleManager role = getSystemService(RoleManager.class);
        boolean callRole = role != null && role.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);

        status(special, R.string.permission_call_screening, callRole);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        boolean listenerAccess = notificationManager != null
                && notificationManager.isNotificationListenerAccessGranted(
                new ComponentName(this, BlitzerListener.class));

        status(special, R.string.permission_notification_listener, listenerAccess);

        permissionAction(special, R.string.permission_call_role,
                callRole, this::requestCallRole);

        permissionAction(special, R.string.permission_notification_access,
                listenerAccess, () -> startActivity(
                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

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
        LinearLayout overview = card();
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        overview.addView(heading);

        TextView appName = body(heading, getString(R.string.about_title));
        appName.setTextSize(18);
        appName.setTypeface(null, Typeface.BOLD);
        TextView version = body(heading,
                getString(R.string.about_version_inline, versionName()));
        version.setTextSize(13);
        version.setTextColor(color(R.color.ui_control_accent));
        version.setPadding(dp(12), 0, 0, 0);
        version.setSingleLine(true);

        TextView introduction = secondary(overview, getString(R.string.about_text));
        introduction.setPadding(0, dp(8), 0, 0);

        TextView featuresHeading = body(overview, getString(R.string.about_features_title));
        featuresHeading.setTypeface(null, Typeface.BOLD);
        featuresHeading.setTextSize(15);
        featuresHeading.setPadding(0, dp(14), 0, dp(5));
        secondary(overview, getString(R.string.about_features_text));

        LinearLayout naming = card();
        title(naming, R.string.about_name_title);
        secondary(naming, getString(R.string.about_name_text));

        LinearLayout privacy = card();
        title(privacy, R.string.about_privacy_title);
        secondary(privacy, getString(R.string.about_privacy));
        TextView notice = secondary(privacy, getString(R.string.about_notice));
        notice.setPadding(0, dp(10), 0, 0);
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
