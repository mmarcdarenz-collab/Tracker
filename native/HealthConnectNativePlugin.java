package com.mrcdrnzz.dailytracker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
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
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.units.Energy;
import android.os.Build;
import android.os.OutcomeReceiver;

import androidx.health.connect.client.HealthConnectClient;



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

    private static final String PREFERRED_HEALTH_SOURCE = "nl.appyhapps.healthsync";

    private boolean isPreferredSource(Record r) {
        try {
            return r != null
                && r.getMetadata() != null
                && r.getMetadata().getDataOrigin() != null
                && PREFERRED_HEALTH_SOURCE.equals(r.getMetadata().getDataOrigin().getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private <T extends Record> List<T> preferHealthSync(List<T> records) {
        List<T> preferred = new java.util.ArrayList<>();
        for (T r : records) if (isPreferredSource(r)) preferred.add(r);
        return preferred.isEmpty() ? records : preferred;
    }

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
            // Android's Jetpack API exposes the correct Health Connect settings
            // action for the Android version running on this device.
            Intent intent = new Intent(HealthConnectClient.getHealthConnectSettingsAction());

            if (intent.resolveActivity(getContext().getPackageManager()) == null) {
                call.reject("Android could not find Health Connect settings.");
                return;
            }

            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not open Health Connect settings.", e);
        }
    }

    private boolean hasReadPermissions() {
        Context c = getContext();
        return c.checkSelfPermission(HealthPermissions.READ_SLEEP) == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission(HealthPermissions.READ_RESTING_HEART_RATE) == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission(HealthPermissions.READ_HEART_RATE) == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission(HealthPermissions.READ_ACTIVE_CALORIES_BURNED) == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission(HealthPermissions.READ_STEPS) == PackageManager.PERMISSION_GRANTED;
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


    private CompletableFuture<Long> aggregateSteps(Instant start, Instant end) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        TimeInstantRangeFilter filter = new TimeInstantRangeFilter.Builder()
            .setStartTime(start)
            .setEndTime(end)
            .build();

        AggregateRecordsRequest<Long> request =
            new AggregateRecordsRequest.Builder<Long>(filter)
                .addAggregationType(StepsRecord.STEPS_COUNT_TOTAL)
                .build();

        manager().aggregate(
            request,
            getContext().getMainExecutor(),
            new OutcomeReceiver<AggregateRecordsResponse<Long>, HealthConnectException>() {
                @Override public void onResult(AggregateRecordsResponse<Long> response) {
                    Long value = response.get(StepsRecord.STEPS_COUNT_TOTAL);
                    future.complete(value == null ? 0L : value);
                }
                @Override public void onError(HealthConnectException error) {
                    future.completeExceptionally(error);
                }
            }
        );
        return future;
    }

    private CompletableFuture<Double> aggregateActiveCalories(Instant start, Instant end) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        TimeInstantRangeFilter filter = new TimeInstantRangeFilter.Builder()
            .setStartTime(start)
            .setEndTime(end)
            .build();

        AggregateRecordsRequest<Energy> request =
            new AggregateRecordsRequest.Builder<Energy>(filter)
                .addAggregationType(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                .build();

        manager().aggregate(
            request,
            getContext().getMainExecutor(),
            new OutcomeReceiver<AggregateRecordsResponse<Energy>, HealthConnectException>() {
                @Override public void onResult(AggregateRecordsResponse<Energy> response) {
                    Energy value = response.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);
                    future.complete(value == null ? 0.0 : value.getInCalories());
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
        ZoneId zone = ZoneId.systemDefault();
        Instant dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant sleepStart = now.minus(Duration.ofHours(36));

        CompletableFuture<List<SleepSessionRecord>> sleepF =
            read(SleepSessionRecord.class, sleepStart, now);
        CompletableFuture<List<HeartRateRecord>> heartF =
            read(HeartRateRecord.class, dayStart, now);
        CompletableFuture<List<ActiveCaloriesBurnedRecord>> calF =
            read(ActiveCaloriesBurnedRecord.class, dayStart, now);
        CompletableFuture<List<StepsRecord>> stepsF =
            read(StepsRecord.class, dayStart, now);

        CompletableFuture.allOf(sleepF, heartF, calF, stepsF).whenComplete((v, err) -> {
            if (err != null) {
                if (err instanceof Exception) {
                    call.reject("Unable to read Health Connect data.", (Exception) err);
                } else {
                    call.reject("Unable to read Health Connect data.", new Exception(err));
                }
                return;
            }

            try {
                JSObject out = new JSObject();
                out.put("needsPermission", false);

                // 1) SLEEP
                // Prefer records imported by Health Sync, then choose the most recently
                // completed sleep session. If stages are available, count only actual
                // sleeping stages and exclude awake/out-of-bed time.
                List<SleepSessionRecord> sleeps = preferHealthSync(sleepF.join());
                SleepSessionRecord latestSleep = null;
                for (SleepSessionRecord s : sleeps) {
                    if (latestSleep == null || s.getEndTime().isAfter(latestSleep.getEndTime())) {
                        latestSleep = s;
                    }
                }

                if (latestSleep != null) {
                    long sleepMillis = 0L;
                    List<SleepSessionRecord.Stage> stages = latestSleep.getStages();

                    if (stages != null && !stages.isEmpty()) {
                        for (SleepSessionRecord.Stage stage : stages) {
                            int type = stage.getType();
                            boolean sleeping =
                                type == SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING
                                || type == SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT
                                || type == SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP
                                || type == SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_REM;

                            if (sleeping) {
                                sleepMillis += Duration.between(
                                    stage.getStartTime(),
                                    stage.getEndTime()
                                ).toMillis();
                            }
                        }
                    }

                    // Some writers provide no stages. In that case use session duration.
                    if (sleepMillis <= 0L) {
                        sleepMillis = Duration.between(
                            latestSleep.getStartTime(),
                            latestSleep.getEndTime()
                        ).toMillis();
                    }

                    out.put("sleepMinutes", Math.round(sleepMillis / 60000.0));
                    out.put("sleepEndTime", latestSleep.getEndTime().toEpochMilli());
                }

                // 2) HEART RATE
                // Use the newest individual sample from today, not today's average.
                List<HeartRateRecord> hearts = preferHealthSync(heartF.join());
                HeartRateRecord.HeartRateSample latestSample = null;
                for (HeartRateRecord r : hearts) {
                    for (HeartRateRecord.HeartRateSample sample : r.getSamples()) {
                        if (latestSample == null || sample.getTime().isAfter(latestSample.getTime())) {
                            latestSample = sample;
                        }
                    }
                }
                if (latestSample != null) {
                    out.put("workoutHR", latestSample.getBeatsPerMinute());
                    out.put("heartRateTime", latestSample.getTime().toEpochMilli());
                }

                // 3) STEPS
                // Sum only today's records from the preferred bridge source.
                // This avoids adding Samsung/phone and Huawei-imported records together.
                List<StepsRecord> stepsRecords = preferHealthSync(stepsF.join());
                long steps = 0L;
                Instant latestStepEnd = null;
                for (StepsRecord r : stepsRecords) {
                    steps += r.getCount();
                    if (latestStepEnd == null || r.getEndTime().isAfter(latestStepEnd)) {
                        latestStepEnd = r.getEndTime();
                    }
                }
                out.put("steps", steps);
                if (latestStepEnd != null) out.put("stepsTime", latestStepEnd.toEpochMilli());

                // 4) ACTIVE CALORIES
                // Android platform Energy.getInCalories() returns small calories.
                // The UI is kcal, therefore divide by 1000.
                List<ActiveCaloriesBurnedRecord> calorieRecords = preferHealthSync(calF.join());
                double calories = 0.0;
                Instant latestCalEnd = null;
                for (ActiveCaloriesBurnedRecord r : calorieRecords) {
                    calories += r.getEnergy().getInCalories();
                    if (latestCalEnd == null || r.getEndTime().isAfter(latestCalEnd)) {
                        latestCalEnd = r.getEndTime();
                    }
                }
                double kcal = calories / 1000.0;
                out.put("activeCalories", Math.round(kcal));
                if (latestCalEnd != null) out.put("activeCaloriesTime", latestCalEnd.toEpochMilli());
                out.put("preferredSource", PREFERRED_HEALTH_SOURCE);

                call.resolve(out);
            } catch (Exception e) {
                call.reject("Unable to process latest Health Connect data.", e);
            }
        });
    }

    @PluginMethod
    public void diagnose(PluginCall call) {
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
        Instant start = now.minus(Duration.ofDays(7));

        CompletableFuture<List<SleepSessionRecord>> sleepF = read(SleepSessionRecord.class, start, now);
        CompletableFuture<List<RestingHeartRateRecord>> restF = read(RestingHeartRateRecord.class, start, now);
        CompletableFuture<List<HeartRateRecord>> heartF = read(HeartRateRecord.class, start, now);
        CompletableFuture<List<ActiveCaloriesBurnedRecord>> calF = read(ActiveCaloriesBurnedRecord.class, start, now);
        CompletableFuture<List<StepsRecord>> stepsF = read(StepsRecord.class, start, now);

        CompletableFuture.allOf(sleepF, restF, heartF, calF, stepsF).whenComplete((v, err) -> {
            if (err != null) {
                if (err instanceof Exception) {
                    call.reject("Unable to diagnose Health Connect data.", (Exception) err);
                } else {
                    call.reject("Unable to diagnose Health Connect data.", new Exception(err));
                }
                return;
            }

            try {
                List<SleepSessionRecord> sleeps = sleepF.join();
                List<RestingHeartRateRecord> rests = restF.join();
                List<HeartRateRecord> hearts = heartF.join();
                List<ActiveCaloriesBurnedRecord> cals = calF.join();
                List<StepsRecord> steps = stepsF.join();

                long hrSamples = 0;
                for (HeartRateRecord r : hearts) hrSamples += r.getSamples().size();

                long stepTotal = 0;
                for (StepsRecord r : steps) stepTotal += r.getCount();

                double calTotal = 0;
                for (ActiveCaloriesBurnedRecord r : cals) {
                    calTotal += r.getEnergy().getInCalories();
                }
                if (calTotal > 5000) calTotal = calTotal / 1000.0;

                java.util.LinkedHashSet<String> sources = new java.util.LinkedHashSet<>();
                for (SleepSessionRecord r : sleeps)
                    if (r.getMetadata()!=null && r.getMetadata().getDataOrigin()!=null)
                        sources.add(r.getMetadata().getDataOrigin().getPackageName());
                for (RestingHeartRateRecord r : rests)
                    if (r.getMetadata()!=null && r.getMetadata().getDataOrigin()!=null)
                        sources.add(r.getMetadata().getDataOrigin().getPackageName());
                for (HeartRateRecord r : hearts)
                    if (r.getMetadata()!=null && r.getMetadata().getDataOrigin()!=null)
                        sources.add(r.getMetadata().getDataOrigin().getPackageName());
                for (ActiveCaloriesBurnedRecord r : cals)
                    if (r.getMetadata()!=null && r.getMetadata().getDataOrigin()!=null)
                        sources.add(r.getMetadata().getDataOrigin().getPackageName());
                for (StepsRecord r : steps)
                    if (r.getMetadata()!=null && r.getMetadata().getDataOrigin()!=null)
                        sources.add(r.getMetadata().getDataOrigin().getPackageName());

                JSObject out = new JSObject();
                out.put("needsPermission", false);
                out.put("windowDays", 7);
                out.put("sleepRecords", sleeps.size());
                out.put("restingHrRecords", rests.size());
                out.put("heartRateRecords", hearts.size());
                out.put("heartRateSamples", hrSamples);
                out.put("activeCalorieRecords", cals.size());
                out.put("activeCaloriesTotal", Math.round(calTotal));
                out.put("stepRecords", steps.size());
                out.put("stepsTotal", stepTotal);
                out.put("sources", String.join(", ", sources));
                call.resolve(out);
            } catch (Exception e) {
                call.reject("Unable to process Health Connect diagnostics.", e);
            }
        });
    }

}