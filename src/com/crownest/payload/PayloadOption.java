package com.crownest.payload;

import java.nio.file.Path;

/**
 * One selectable payload in the Revshell tab, whatever its source.
 *
 * Both the MSFVenom commands and the local script templates implement this, so
 * the UI treats them uniformly: show a label, show a preview, produce a file.
 */
public interface PayloadOption {

    /** Text shown in the dropdown and the hosted-payload list. */
    String display();

    /** A short source tag, e.g. "MSFVenom" or "Script". */
    String kind();

    /**
     * The command or action that generation will perform, for display only.
     * {@code shell} is the selected target shell (only revshells payloads use it).
     */
    String preview(String ip, String port, String shell);

    /** Produce the payload file under a fresh sub-directory of {@code baseDir}. */
    PayloadGenerator.Result generate(String ip, String port, String shell, Path baseDir);
}
