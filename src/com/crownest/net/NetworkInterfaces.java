package com.crownest.net;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/** Discovery of bindable local addresses. */
public final class NetworkInterfaces {

    /** VPN style interfaces a pentester usually wants first. */
    private static final String[] PRIORITY = {"tun", "tap", "wg", "ppp", "vpn"};

    public record Iface(String name, String address, boolean ipv6, boolean up) {

        public String label() {
            return name + "  -  " + address;
        }

        public boolean isAny() {
            return address.equals("0.0.0.0") || address.equals("::");
        }

        int rank() {
            for (String p : PRIORITY) {
                if (name.startsWith(p)) {
                    return 0;
                }
            }
            if (name.equals("lo")) {
                return 2;
            }
            return 1;
        }
    }

    public static final Iface ANY = new Iface("All interfaces", "0.0.0.0", false, true);

    private NetworkInterfaces() {
    }

    /** All usable addresses, with "All interfaces" first. */
    public static List<Iface> list(boolean includeIpv6) {
        List<Iface> found = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                boolean up;
                try {
                    up = nic.isUp();
                } catch (SocketException e) {
                    up = false;
                }
                if (!up) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.isLinkLocalAddress()) {
                        continue;
                    }
                    boolean v6 = addr instanceof Inet6Address;
                    if (v6 && !includeIpv6) {
                        continue;
                    }
                    if (!(addr instanceof Inet4Address) && !v6) {
                        continue;
                    }
                    String text = addr.getHostAddress();
                    int scope = text.indexOf('%');
                    if (scope >= 0) {
                        text = text.substring(0, scope);
                    }
                    found.add(new Iface(nic.getName(), text, v6, true));
                }
            }
        } catch (SocketException e) {
            // fall through to whatever was collected
        }

        found.sort(Comparator.comparingInt(Iface::rank)
                .thenComparing((Iface i) -> i.ipv6() ? 1 : 0)
                .thenComparing(Iface::name));

        List<Iface> result = new ArrayList<>();
        result.add(ANY);
        result.addAll(found);
        if (result.size() == 1) {
            result.add(new Iface("lo", "127.0.0.1", false, true));
        }
        return Collections.unmodifiableList(result);
    }

    /** Best guess outbound IPv4, used for building URLs when bound to 0.0.0.0. */
    public static String primaryAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            // TEST-NET-1. Nothing is actually transmitted for a UDP connect.
            socket.connect(InetAddress.getByName("192.0.2.1"), 9);
            String addr = socket.getLocalAddress().getHostAddress();
            if (addr != null && !addr.equals("0.0.0.0")) {
                return addr;
            }
        } catch (Exception e) {
            // ignored, fall back below
        }
        return "127.0.0.1";
    }

    /** True when the address/port can currently be bound. */
    public static boolean portAvailable(String host, int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName(host), port), 1);
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
