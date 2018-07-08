package com.cloudinary.android;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.cloudinary.android.callback.UploadStatus;
import com.cloudinary.android.policy.UploadPolicy;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;

public class WorkManagerStrategy implements BackgroundRequestStrategy {
    private static final String WOKR_MANAGER_TAG = "CLD_WORKERS";

    @Override
    public void init(Context context) {
        // none required
    }

    @Override
    public void doDispatch(UploadRequest request) {
        WorkManager.getInstance().enqueue(adapt(request));
    }

    @Override
    public void executeRequestsNow(int howMany) {

    }

    @Override
    public boolean cancelRequest(String requestId) {
        return false;
    }

    @Override
    public int cancelAllRequests() {
        return 0;
    }

    @Override
    public int getPendingImmediateJobsCount() {
        return 0;
    }

    @Override
    public int getRunningJobsCount() {
        return 0;
    }

    public static class RequestWorker extends Worker {
        @NonNull
        @Override
        public Worker.Result doWork() {
            WorkManagerRequestParams params = new WorkManagerRequestParams(getInputData());
            params.putInt(DefaultRequestProcessor.ERROR_COUNT_PARAM, getRunAttemptCount());
            UploadStatus result = MediaManager.get().processRequest(this.getApplicationContext(), params);
            return adapt(result);
        }
    }

    private static Worker.Result adapt(UploadStatus result) {
        switch (result) {
            case FAILURE:
                return Worker.Result.FAILURE;
            case SUCCESS:
                return Worker.Result.SUCCESS;
            case RESCHEDULE:
                return Worker.Result.RETRY;
        }

        // unexpected result, we don't want to retry because we have no idea why it failed.
        return Worker.Result.FAILURE;
    }

    static OneTimeWorkRequest adapt(UploadRequest request) {

        WorkManagerRequestParams data = new WorkManagerRequestParams(new Data.Builder().build());
        request.populateParamsFromFields(data);

        UploadPolicy policy = request.getUploadPolicy();

        Constraints.Builder constraintsBuilder = new Constraints.Builder()
                .setRequiresCharging(policy.isRequiresCharging())
                .setRequiredNetworkType(adaptNetworkType(policy.getNetworkType()));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            constraintsBuilder.setRequiresDeviceIdle(policy.isRequiresIdle());
        }

        OneTimeWorkRequest huh = new OneTimeWorkRequest.Builder(RequestWorker.class)
                .setInitialDelay(request.getTimeWindow().getMinLatencyOffsetMillis(), TimeUnit.MILLISECONDS)
                .setInputData(data.getData())
                .setBackoffCriteria(adaptBackoffPolicy(policy.getBackoffPolicy()), policy.getBackoffMillis(), TimeUnit.MILLISECONDS)
                .addTag(WOKR_MANAGER_TAG)
                .setConstraints(constraintsBuilder.build())
                .build();

        return huh;
    }

    private static BackoffPolicy adaptBackoffPolicy(UploadPolicy.BackoffPolicy backoffPolicy) {
        switch (backoffPolicy) {
            case LINEAR:
                return BackoffPolicy.LINEAR;
            case EXPONENTIAL:
            default:
                return BackoffPolicy.EXPONENTIAL;
        }
    }

    private static NetworkType adaptNetworkType(UploadPolicy.NetworkType networkType) {
        switch (networkType){

            case NONE:
                return NetworkType.NOT_REQUIRED;
            case ANY:
                return NetworkType.CONNECTED;
            case UNMETERED:
                return NetworkType.UNMETERED;
        }

        return NetworkType.CONNECTED;
    }

    private static final class WorkManagerRequestParams implements RequestParams {
        private Data data;

        private WorkManagerRequestParams(Data data) {
            this.data = data;
        }

        @Override
        public void putString(String key, String value) {
            data = new Data.Builder().putAll(data).putString(key, value).build();
        }

        @Override
        public void putInt(String key, int value) {
            data = new Data.Builder().putAll(data).putInt(key, value).build();
        }

        @Override
        public void putLong(String key, long value) {
            data = new Data.Builder().putAll(data).putLong(key, value).build();
        }

        @Override
        public String getString(String key, String defaultValue) {
            return data.getString(key, defaultValue);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return data.getInt(key, defaultValue);
        }

        @Override
        public long getLong(String key, long defaultValue) {
            return data.getLong(key, defaultValue);
        }

        public Data getData() {
            return data;
        }
    }

    private static class RequestParamsMap extends HashMap<String,Object> implements RequestParams {
        @Override
        public void putString(String key, String value) {
            put(key, value);
        }

        @Override
        public void putInt(String key, int value) {
            put(key, value);
        }

        @Override
        public void putLong(String key, long value) {
            put(key, value);
        }

        @Override
        public String getString(String key, String defaultValue) {
            return containsKey(key) ? (String) get(key) : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return containsKey(key) ? (int) get(key) : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            return containsKey(key) ? (long) get(key) : defaultValue;
        }
    }
}
