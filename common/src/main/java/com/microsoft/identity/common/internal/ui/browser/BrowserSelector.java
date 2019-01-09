// Copyright (c) Microsoft Corporation.
// All rights reserved.
//
// This code is licensed under the MIT License.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files(the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions :
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
package com.microsoft.identity.common.internal.ui.browser;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsService;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.microsoft.identity.common.R;
import com.microsoft.identity.common.exception.ClientException;
import com.microsoft.identity.common.exception.ErrorStrings;
import com.microsoft.identity.common.internal.authorities.Authority;
import com.microsoft.identity.common.internal.authorities.AuthorityDeserializer;
import com.microsoft.identity.common.internal.authorities.AzureActiveDirectoryAudience;
import com.microsoft.identity.common.internal.authorities.AzureActiveDirectoryAudienceDeserializer;
import com.microsoft.identity.common.internal.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class BrowserSelector {
    private static final String TAG = BrowserSelector.class.getSimpleName();
    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";

    /**
     * Searches through all browsers for the best match.
     * Browsers are evaluated in the order returned by the package manager,
     * which should indirectly match the user's preferences.
     * First browser in the list will be preferred no matter weather or not the custom tabs supported.
     *
     * @param context {@link Context} to use for accessing {@link PackageManager}.
     * @return Browser selected to use.
     */
    public static Browser select(final Context context) throws ClientException {
        /* 1. Load browser metadata
           2. compare the default browser */
        final List<BrowserPair> browserPairs = loadBrowserMetadata(context, R.raw.browser_metadata);

        final List<Browser> allBrowsers = getAllBrowsers(context);

        final List<Browser> filteredBrowsers = new ArrayList<>();

        for (Browser browser : allBrowsers) {
            for (BrowserPair browserPair : browserPairs) {
                if (browserPair.matches(browser)) {
                    filteredBrowsers.add(browser);
                }
            }
        }

        if (!filteredBrowsers.isEmpty()) {
            Logger.verbose(TAG, "Select the browser to launch.");
            Logger.verbosePII(TAG, "Browser's package name: " + allBrowsers.get(0).getPackageName() + " version: " + allBrowsers.get(0).getVersion());
            return allBrowsers.get(0);
        } else {
            Logger.error(TAG, "No available browser installed on the device.", null);
            throw new ClientException(ErrorStrings.NO_AVAILABLE_BROWSER_FOUND, "No available browser installed on the device.");
        }
    }

    /**
     * Load the browser metadata json file.
     * @param context Context
     * @param browserMetadataId int
     * @return list of browser pairs.
     */
    static List<BrowserPair> loadBrowserMetadata(final Context context, final int browserMetadataId) {
        InputStream browserMetadataStream = context.getResources().openRawResource(browserMetadataId);
        byte[] buffer;
        List<BrowserPair> browserPairs = new ArrayList<>();

        try {
            buffer = new byte[browserMetadataStream.available()];
            browserMetadataStream.read(buffer);
            final String browserMetadata = new String(buffer);
            Type collectionType = new TypeToken<List<BrowserPair>>(){}.getType();
            browserPairs = new Gson().fromJson(browserMetadata,collectionType);
        } catch (final IOException exception) {
            //TODO
        }

        return browserPairs;
    }

    /**
     * Retrieves the full list of browsers installed on the device.
     * If the browser supports custom tabs, it will {@link Browser#mIsCustomTabsServiceSupported}
     * flag set to `true` in one and `false` in the other. The list is in the
     * order returned by the package manager, so indirectly reflects the user's preferences
     * (i.e. their default browser, if set, should be the first entry in the list).
     */
    public static List<Browser> getAllBrowsers(final Context context) {
        //get the list of browsers
        final Intent BROWSER_INTENT = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("http://www.example.com"));

        List<Browser> browserList = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        int queryFlag = PackageManager.GET_RESOLVED_FILTER;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            queryFlag |= PackageManager.MATCH_DEFAULT_ONLY;
        }

        List<ResolveInfo> resolvedActivityList =
                pm.queryIntentActivities(BROWSER_INTENT, queryFlag);

        for (ResolveInfo info : resolvedActivityList) {
            // ignore handlers which are not browsers
            if (!isFullBrowser(info)) {
                continue;
            }

            try {
                PackageInfo packageInfo = pm.getPackageInfo(
                        info.activityInfo.packageName,
                        PackageManager.GET_SIGNATURES);

                //TODO if the browser is in the block list, do not add it into the return browserList.
                if (isCustomTabsServiceSupported(context, packageInfo)) {
                    //if the browser has custom tab enabled, set the custom tab support as true.
                    browserList.add(new Browser(packageInfo, true));
                } else {
                    browserList.add(new Browser(packageInfo, false));
                }
            } catch (PackageManager.NameNotFoundException e) {
                // a browser cannot be generated without the package info
            }
        }

        Logger.verbose(TAG, null, "Found " + browserList.size() + " browsers.");
        return browserList;
    }

    private static boolean isCustomTabsServiceSupported(@NonNull final Context context, @NonNull final PackageInfo packageInfo) {
        Intent serviceIntent = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
        serviceIntent.setPackage(packageInfo.packageName);
        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(serviceIntent, 0);
        return !(resolveInfos == null || resolveInfos.isEmpty());
    }

    private static boolean isFullBrowser(final ResolveInfo resolveInfo) {
        // The filter must match ACTION_VIEW, CATEGORY_BROWSEABLE, and at least one scheme,
        if (!resolveInfo.filter.hasAction(Intent.ACTION_VIEW)
                || !resolveInfo.filter.hasCategory(Intent.CATEGORY_BROWSABLE)
                || resolveInfo.filter.schemesIterator() == null) {
            return false;
        }

        // The filter must not be restricted to any particular set of authorities
        if (resolveInfo.filter.authoritiesIterator() != null) {
            return false;
        }

        // The filter must support both HTTP and HTTPS.
        boolean supportsHttp = false;
        boolean supportsHttps = false;
        Iterator<String> schemeIter = resolveInfo.filter.schemesIterator();
        while (schemeIter.hasNext()) {
            String scheme = schemeIter.next();
            supportsHttp |= SCHEME_HTTP.equals(scheme);
            supportsHttps |= SCHEME_HTTPS.equals(scheme);

            if (supportsHttp && supportsHttps) {
                return true;
            }
        }

        // at least one of HTTP or HTTPS is not supported
        return false;
    }
}