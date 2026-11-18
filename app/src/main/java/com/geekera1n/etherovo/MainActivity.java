package com.geekera1n.etherovo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutDisconnected;
    private ScrollView layoutConnected;
    private MaterialToolbar topAppBar;
    private TextView tvInterfaceName;
    private Button btnAddIp, btnAddRoute, btnResetInterface;
    private ListView lvInterfaceDetails, lvIpAddresses, lvRoutes;
    private TextView tvDisconnectedStatus;

    private InfoAdapter detailsAdapter, ipAdapter, routeAdapter;
    private final List<InfoItem> detailsList = new ArrayList<>();
    private final List<InfoItem> ipList = new ArrayList<>();
    private final List<InfoItem> routeList = new ArrayList<>();

    private String currentInterfaceName = "";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;
    private boolean isAutoRefreshing = false;

    // +++ FIX 1: 弹窗期间暂停自动刷新标志
    private boolean isDialogShowing = false;

    // +++ FIX 4: 缓存 Root Shell 实例，避免每次新建进程
    private Process cachedRootProcess = null;
    private java.io.BufferedWriter cachedRootWriter = null;
    private java.io.BufferedReader cachedRootReader = null;

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "EtherOvO_Persistence";
    private static final String KEY_PERSISTENT_IPS = "persistent_ips";
    private static final String KEY_PERSISTENT_ROUTES = "persistent_routes";
    private boolean wasInterfaceUp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initViews();
        setupListeners();
        refreshAllInfo(true);
    }

    @Override
    protected void onResume() { super.onResume(); startAutoRefresh(); }
    @Override
    protected void onPause() { super.onPause(); stopAutoRefresh(); }

    private void initViews() {
        layoutDisconnected = findViewById(R.id.layoutDisconnected);
        layoutConnected = findViewById(R.id.layoutConnected);
        topAppBar = findViewById(R.id.topAppBar);
        tvInterfaceName = findViewById(R.id.tvInterfaceName);
        lvInterfaceDetails = findViewById(R.id.lvInterfaceDetails);
        btnAddIp = findViewById(R.id.btnAddIp);
        btnAddRoute = findViewById(R.id.btnAddRoute);
        btnResetInterface = findViewById(R.id.btnResetInterface);
        lvIpAddresses = findViewById(R.id.lvIpAddresses);
        lvRoutes = findViewById(R.id.lvRoutes);
        tvDisconnectedStatus = findViewById(R.id.tv_disconnected_status);

        detailsAdapter = new InfoAdapter(this, detailsList);
        ipAdapter = new InfoAdapter(this, ipList);
        routeAdapter = new InfoAdapter(this, routeList);

        lvInterfaceDetails.setAdapter(detailsAdapter);
        lvIpAddresses.setAdapter(ipAdapter);
        lvRoutes.setAdapter(routeAdapter);
    }

    private void setupListeners() {
        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                refreshAllInfo(true);
                return true;
            }
            if (item.getItemId() == R.id.action_info) {
                showAboutDialog();
                return true;
            }
            return false;
        });

        if (RootUtil.isRootAvailable()) {
            btnAddIp.setOnClickListener(v -> showAddIpDialog());
            btnAddRoute.setOnClickListener(v -> showAddRouteDialog());
