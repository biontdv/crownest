package com.crownest.payload;

import java.nio.file.Path;

/** A {@link PayloadOption} backed by one MSFVenom catalogue command. */
public final class MsfvenomOption implements PayloadOption {

    private final MsfvenomCatalog.Entry entry;

    public MsfvenomOption(MsfvenomCatalog.Entry entry) {
        this.entry = entry;
    }

    @Override
    public String display() {
        return "msfvenom: " + entry.name();
    }

    @Override
    public String kind() {
        return "MSFVenom";
    }

    @Override
    public String preview(String ip, String port, String shell) {
        String shownIp = ip == null || ip.isEmpty() ? "{ip}" : ip;
        String shownPort = port == null || port.isEmpty() ? "{port}" : port;
        return entry.template().replace("{ip}", shownIp).replace("{port}", shownPort);
    }

    @Override
    public PayloadGenerator.Result generate(String ip, String port, String shell, Path baseDir) {
        return PayloadGenerator.generate(entry, ip, port, baseDir);
    }

    @Override
    public String toString() {
        return display();
    }
}
