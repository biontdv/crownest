package com.crownest.security;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Comma or newline separated list of literal IPs and CIDR ranges.
 * An empty list allows everything.
 */
public final class IpAllowList {

    private record Rule(byte[] network, int prefixBits) {

        boolean matches(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    private final List<Rule> rules = new ArrayList<>();
    private final List<String> invalid = new ArrayList<>();

    public IpAllowList(String spec) {
        if (spec == null) {
            return;
        }
        for (String raw : spec.replace(',', '\n').split("\n")) {
            String entry = raw.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            Rule rule = parse(entry);
            if (rule == null) {
                invalid.add(entry);
            } else {
                rules.add(rule);
            }
        }
    }

    private static Rule parse(String entry) {
        String host = entry;
        int prefix = -1;
        int slash = entry.indexOf('/');
        if (slash >= 0) {
            host = entry.substring(0, slash);
            try {
                prefix = Integer.parseInt(entry.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        InetAddress addr;
        try {
            // Reject hostnames: only literal addresses are meaningful here.
            if (!host.matches("[0-9.]+") && !host.contains(":")) {
                return null;
            }
            addr = InetAddress.getByName(host.trim());
        } catch (UnknownHostException e) {
            return null;
        }
        byte[] bytes = addr.getAddress();
        int maxBits = bytes.length * 8;
        if (prefix < 0) {
            prefix = maxBits;
        }
        if (prefix > maxBits) {
            return null;
        }
        return new Rule(bytes, prefix);
    }

    public boolean isActive() {
        return !rules.isEmpty();
    }

    public List<String> invalidEntries() {
        return List.copyOf(invalid);
    }

    /** True when the client address is permitted. */
    public boolean allows(String clientAddress) {
        if (rules.isEmpty()) {
            return true;
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(clientAddress);
        } catch (UnknownHostException e) {
            return false;
        }
        byte[] bytes = addr.getAddress();

        // Compare IPv4-mapped IPv6 clients against IPv4 rules too.
        byte[] mappedV4 = null;
        if (bytes.length == 16 && isV4Mapped(bytes)) {
            mappedV4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
        }
        for (Rule rule : rules) {
            if (rule.matches(bytes)) {
                return true;
            }
            if (mappedV4 != null && rule.matches(mappedV4)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isV4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
    }

    public static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isIpv4(String address) {
        try {
            return InetAddress.getByName(address) instanceof Inet4Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
