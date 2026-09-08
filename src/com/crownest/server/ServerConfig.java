package com.crownest.server;

import com.crownest.net.NetworkInterfaces;
import com.crownest.vfs.ShareItem;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Mutable settings shared between the UI and the server. */
public final class ServerConfig {

    // --- network
    public String bindAddress = "0.0.0.0";
    public String interfaceName = "All interfaces";
    public int port = 8080;

    // --- transport (off by default: plain HTTP so download cradles work
    //     without a self-signed-certificate bypass; enable in Advanced)
    public boolean tls = false;

    // --- access control
    public boolean authEnabled = false;
    public String username = "";
    public String passwordHash = "";
    public boolean secretPrefix = false;
    public String urlPrefix = "";
    public String ipAllowList = "";

    // --- behaviour
    public boolean directoryListing = true;
    public boolean allowUpload = false;
    public boolean followSymlinks = false;

    // --- limits
    public int ratePerMinute = 480;
    public int maxFailures = 8;
    public int lockoutSeconds = 300;
    public int maxUploadMb = 2048;

    public String scheme() {
        return tls ? "https" : "http";
    }

    /** The host used when building copyable URLs. */
    public String displayHost() {
        if (bindAddress == null || bindAddress.isBlank()
                || bindAddress.equals("0.0.0.0") || bindAddress.equals("::")) {
            return NetworkInterfaces.primaryAddress();
        }
        return bindAddress;
    }

    public String baseUrl() {
        String host = displayHost();
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        int defaultPort = tls ? 443 : 80;
        String netloc = (port == defaultPort) ? host : host + ":" + port;
        StringBuilder url = new StringBuilder(scheme()).append("://").append(netloc);
        if (urlPrefix != null && !urlPrefix.isEmpty()) {
            url.append('/').append(urlPrefix);
        }
        return url.toString();
    }

    public String itemUrl(ShareItem item) {
        if (item == null) {
            return "";
        }
        return baseUrl() + "/" + encodeSegment(item.name()) + (item.isDirectory() ? "/" : "");
    }

    /** Percent-encode one path segment without turning spaces into plus signs. */
    public static String encodeSegment(String segment) {
        try {
            return URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return segment;
        }
    }

    public ServerConfig copy() {
        ServerConfig c = new ServerConfig();
        c.bindAddress = bindAddress;
        c.interfaceName = interfaceName;
        c.port = port;
        c.tls = tls;
        c.authEnabled = authEnabled;
        c.username = username;
        c.passwordHash = passwordHash;
        c.secretPrefix = secretPrefix;
        c.urlPrefix = urlPrefix;
        c.ipAllowList = ipAllowList;
        c.directoryListing = directoryListing;
        c.allowUpload = allowUpload;
        c.followSymlinks = followSymlinks;
        c.ratePerMinute = ratePerMinute;
        c.maxFailures = maxFailures;
        c.lockoutSeconds = lockoutSeconds;
        c.maxUploadMb = maxUploadMb;
        return c;
    }
}
