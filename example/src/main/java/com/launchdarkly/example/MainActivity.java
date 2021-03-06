package com.launchdarkly.example;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonNull;
import com.launchdarkly.android.LDAllFlagsListener;
import com.launchdarkly.android.ConnectionInformation;
import com.launchdarkly.android.FeatureFlagChangeListener;
import com.launchdarkly.android.LDClient;
import com.launchdarkly.android.LDConfig;
import com.launchdarkly.android.LDFailure;
import com.launchdarkly.android.LDStatusListener;
import com.launchdarkly.android.LDUser;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private LDClient ldClient;
    private LDStatusListener ldStatusListener;
    private LDAllFlagsListener allFlagsListener;

    private void updateStatusString(final ConnectionInformation connectionInformation) {
        if (Looper.myLooper() != MainActivity.this.getMainLooper()) {
            (new Handler(MainActivity.this.getMainLooper())).post(new Runnable() {
                @Override
                public void run() {
                    updateStatusString(connectionInformation);
                }
            });
        } else {
            TextView connection = MainActivity.this.findViewById(R.id.connection_status);
            Long lastSuccess = connectionInformation.getLastSuccessfulConnection();
            Long lastFailure = connectionInformation.getLastFailedConnection();

            String result = String.format(Locale.US, "Mode: %s\nSuccess at: %s\nFailure at: %s\nFailure type: %s",
                    connectionInformation.getConnectionMode().toString(),
                    lastSuccess == null ? "Never" : new Date(lastSuccess).toString(),
                    lastFailure == null ? "Never" : new Date(lastFailure).toString(),
                    connectionInformation.getLastFailure() != null ?
                            connectionInformation.getLastFailure().getFailureType()
                            : "");
            connection.setText(result);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupEval();
        setupFlushButton();
        setupTrackButton();
        setupIdentifyButton();
        setupOfflineSwitch();
        setupListeners();

        LDConfig ldConfig = new LDConfig.Builder()
                .setMobileKey("MOBILE_KEY")
                .setUseReport(false) // change to `true` if the request is to be REPORT'ed instead of GET'ed
                .build();

        LDUser user = new LDUser.Builder("user key")
                .email("fake@example.com")
                .build();

        Future<LDClient> initFuture = LDClient.init(this.getApplication(), ldConfig, user);
        try {
            ldClient = initFuture.get(10, TimeUnit.SECONDS);
            updateStatusString(ldClient.getConnectionInformation());
            ldClient.registerStatusListener(ldStatusListener);
            ldClient.registerAllFlagsListener(allFlagsListener);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Timber.e(e, "Exception when awaiting LaunchDarkly Client initialization");
        }
    }

    private void setupListeners() {
        ldStatusListener = new LDStatusListener() {
            @Override
            public void onConnectionModeChanged(final ConnectionInformation connectionInformation) {
                updateStatusString(connectionInformation);
            }

            @Override
            public void onInternalFailure(final LDFailure ldFailure) {
                new Handler(MainActivity.this.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, ldFailure.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
                updateStatusString(ldClient.getConnectionInformation());
            }
        };

        allFlagsListener = new LDAllFlagsListener() {
            @Override
            public void onChange(final List<String> flagKey) {
                new Handler(MainActivity.this.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder flags = new StringBuilder("Updated flags: ");
                        for (String flag : flagKey) {
                            flags.append(flag).append(" ");
                        }
                        Toast.makeText(MainActivity.this, flags.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
                updateStatusString(ldClient.getConnectionInformation());
            }
        };
    }

    private void setupFlushButton() {
        Button flushButton = findViewById(R.id.flush_button);
        flushButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Timber.i("flush onClick");
                MainActivity.this.doSafeClientAction(new LDClientFunction() {
                    @Override
                    public void call() {
                        ldClient.flush();
                    }
                });
            }
        });
    }

    private interface LDClientFunction {
        void call();
    }

    private interface LDClientGetFunction<V> {
        V get();
    }

    private void doSafeClientAction(LDClientFunction function) {
        if (ldClient != null) {
            function.call();
        }
    }

    @Nullable
    private <V> V doSafeClientGet(LDClientGetFunction<V> function) {
        if (ldClient != null) {
            return function.get();
        }
        return null;
    }

    private void setupTrackButton() {
        Button trackButton = findViewById(R.id.track_button);
        trackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Timber.i("track onClick");
                MainActivity.this.doSafeClientAction(new LDClientFunction() {
                    @Override
                    public void call() {
                        ldClient.track("Android event name");
                    }
                });
            }
        });
    }

    private void setupIdentifyButton() {
        Button identify = findViewById(R.id.identify_button);
        identify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Timber.i("identify onClick");
                String userKey = ((EditText) MainActivity.this.findViewById(R.id.userKey_editText)).getText().toString();
                final LDUser updatedUser = new LDUser.Builder(userKey).build();
                MainActivity.this.doSafeClientAction(new LDClientFunction() {
                    @Override
                    public void call() {
                        ldClient.identify(updatedUser);
                    }
                });
            }
        });
    }

    private void setupOfflineSwitch() {
        Switch offlineSwitch = findViewById(R.id.offlineSwitch);
        offlineSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    MainActivity.this.doSafeClientAction(new LDClientFunction() {
                        @Override
                        public void call() {
                            ldClient.setOffline();
                        }
                    });
                } else {
                    MainActivity.this.doSafeClientAction(new LDClientFunction() {
                        @Override
                        public void call() {
                            ldClient.setOnline();
                        }
                    });
                }
            }
        });
    }

    private void setupEval() {
        final Spinner spinner = findViewById(R.id.type_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.types_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        Button evalButton = findViewById(R.id.eval_button);
        evalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Timber.i("eval onClick");
                final String flagKey = ((EditText) MainActivity.this.findViewById(R.id.feature_flag_key)).getText().toString();

                String type = spinner.getSelectedItem().toString();
                final String result;
                String logResult;
                switch (type) {
                    case "String":
                        result = MainActivity.this.doSafeClientGet(new LDClientGetFunction<String>() {
                            @Override
                            public String get() {
                                return ldClient.stringVariation(flagKey, "default");
                            }
                        });
                        logResult = result == null ? "no result" : result;
                        Timber.i(logResult);
                        ((TextView) MainActivity.this.findViewById(R.id.result_textView)).setText(result);
                        MainActivity.this.doSafeClientAction(new LDClientFunction() {
                            @Override
                            public void call() {
                                ldClient.registerFeatureFlagListener(flagKey, new FeatureFlagChangeListener() {
                                    @Override
                                    public void onFeatureFlagChange(String flagKey1) {
                                        ((TextView) MainActivity.this.findViewById(R.id.result_textView))
                                                .setText(ldClient.stringVariation(flagKey1, "default"));
                                    }
                                });
                            }
                        });
                        break;
                    case "Boolean":
                        result = MainActivity.this.doSafeClientGet(new LDClientGetFunction<String>() {
                            @Override
                            public String get() {
                                return ldClient.boolVariation(flagKey, false).toString();
                            }
                        });
                        logResult = result == null ? "no result" : result;
                        Timber.i(logResult);
                        ((TextView) MainActivity.this.findViewById(R.id.result_textView)).setText(result);
                        break;
                    case "Integer":
                        result = MainActivity.this.doSafeClientGet(new LDClientGetFunction<String>() {
                            @Override
                            public String get() {
                                return ldClient.intVariation(flagKey, 0).toString();
                            }
                        });
                        logResult = result == null ? "no result" : result;
                        Timber.i(logResult);
                        ((TextView) MainActivity.this.findViewById(R.id.result_textView)).setText(result);
                        break;
                    case "Float":
                        result = MainActivity.this.doSafeClientGet(new LDClientGetFunction<String>() {
                            @Override
                            public String get() {
                                return ldClient.floatVariation(flagKey, 0F).toString();
                            }
                        });
                        logResult = result == null ? "no result" : result;
                        Timber.i(logResult);
                        ((TextView) MainActivity.this.findViewById(R.id.result_textView)).setText(result);
                        break;
                    case "Json":
                        result = MainActivity.this.doSafeClientGet(new LDClientGetFunction<String>() {
                            @Override
                            public String get() {
                                return ldClient.jsonVariation(flagKey, JsonNull.INSTANCE).toString();
                            }
                        });
                        logResult = result == null ? "no result" : result;
                        Timber.i(logResult);
                        ((TextView) MainActivity.this.findViewById(R.id.result_textView)).setText(result);
                        break;
                }
            }
        });
    }

}
