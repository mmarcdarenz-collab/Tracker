package com.mrcdrnzz.dailytracker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.health.connect.HealthConnectException;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.SleepSessionRecord;
import android.health.connect.datatypes.RestingHeartRateRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.os.Build;
import android.os.OutcomeReceiver;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@CapacitorPlugin(name = "HealthConnectNative")
public class HealthConnectNativePlugin extends Plugin {

    private HealthConnectManager manager() {
        if (Build.VERSION.SDK_INT < 34) return null;
        return (HealthConnectManager) getContext().getSystemService(Context.HEALTHCONNECT_SERVICE);
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject o = new JSObject();
        o.put("available", manager() != null);
        call.resolve(o);
    }

    @PluginMethod
    public void openPermissions(PluginCall call) {
        if (manager() == null) {
            call.reject("Health Connect is unavailable on this Android version.");
            return;
        }
        try {
            Intent intent = new Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, getContext().getPackageName());
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not open Health Connect permissions.", e);
        }
    }

    private boolean hasReadPermissions() {
        Context c = getContext();
        return c.checkSelfPermission(HealthPermissions.READ_SLEEP) == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission(HealthPermissions.READ_RESTING_HEART_RATE) == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission(HealthPermissions.READ_HEART_RATE) == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission(HealthPermissions.READ_ACTIVE_CALORIES_BURNED) == PackageManager.PERMISSION_GRANTED;
    }

    private <T extends Record> CompletableFuture<List<T>> read(Class<T> cls, Instant start, Instant end) {
        CompletableFuture<List<T>> future = new CompletableFuture<>();
        TimeInstantRangeFilter filter = new TimeInstantRangeFilter.Builder()
            .setStartTime(start)
            .setEndTime(end)
            .build();

        ReadRecordsRequestUsingFilters<T> request =
            new ReadRecordsRequestUsingFilters.Builder<>(cls)
                .setTimeRangeFilter(filter)
                .setPageSize(5000)
                .build();

        manager().readRecords(
            request,
            getContext().getMainExecutor(),
            new OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException>() {
                @Override public void onResult(ReadRecordsResponse<T> response) {
                    future.complete(response.getRecords());
                }
                @Override public void onError(HealthConnectException error) {
                    future.completeExceptionally(error);
                }
            }
        );
        return future;
    }

    @PluginMethod
    public void readSummary(PluginCall call) {
        HealthConnectManager mgr = manager();
        if (mgr == null) {
            call.reject("Health Connect is unavailable.");
            return;
        }

        if (!hasReadPermissions()) {
            JSObject o = new JSObject();
            o.put("needsPermission", true);
            call.resolve(o);
            return;
        }

        Instant now = Instant.now();
        Instant sleepStart = now.minus(Duration.ofHours(36));
        Instant dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        CompletableFuture<List<SleepSessionRecord>> sleepF = read(SleepSessionRecord.class, sleepStart, now);
        CompletableFuture<List<RestingHeartRateRecord>> restF = read(RestingHeartRateRecord.class, sleepStart, now);
        CompletableFuture<List<HeartRateRecord>> heartF = read(HeartRateRecord.class, dayStart, now);
        CompletableFuture<List<ActiveCaloriesBurnedRecord>> calF = read(ActiveCaloriesBurnedRecord.class, dayStart, now);

        CompletableFuture.allOf(sleepF, restF, heartF, calF).whenComplete((v, err) -> {
            if (err != null) {
                call.reject("Unable to read Health Connect data.", err);
                return;
            }
            try {
                JSObject out = new JSObject();
                out.put("needsPermission", false);

                List<SleepSessionRecord> sleeps = sleepF.join();
                SleepSessionRecord latestSleep = null;
                for (SleepSessionRecord s : sleeps) {
                    if (latestSleep == null || s.getEndTime().isAfter(latestSleep.getEndTime())) latestSleep = s;
                }
                if (latestSleep != null) {
                    long mins = Duration.between(latestSleep.getStartTime(), latestSleep.getEndTime()).toMinutes();
                    out.put("sleepMinutes", mins);
                }

                List<RestingHeartRateRecord> rests = restF.join();
                RestingHeartRateRecord latestRest = null;
                for (RestingHeartRateRecord r : rests) {
                    if (latestRest == null || r.getTime().isAfter(latestRest.getTime())) latestRest = r;
                }
                if (latestRest != null) out.put("restingHR", latestRest.getBeatsPerMinute());

                long hrSum = 0;
                long hrCount = 0;
                for (HeartRateRecord r : heartF.join()) {
                    for (HeartRateRecord.HeartRateSample sample : r.getSamples()) {
                        hrSum += sample.getBeatsPerMinute();
                        hrCount++;
                    }
                }
                if (hrCount > 0) out.put("workoutHR", Math.round((double) hrSum / hrCount));

                double calories = 0;
                for (ActiveCaloriesBurnedRecord r : calF.join()) {
                    calories += r.getEnergy().getInCalories();
                }
                if (calories > 5000) calories = calories / 1000.0;
                out.put("activeCalories", Math.round(calories));

                call.resolve(out);
            } catch (Exception e) {
                call.reject("Unable to process Health Connect data.", e);
            }
        });
    }
}
