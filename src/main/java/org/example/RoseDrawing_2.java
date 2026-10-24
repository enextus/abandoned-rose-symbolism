package org.example;

import java.io.IOException;

/**
 * Legacy launcher kept for backward compatibility.
 *
 * Delegates all command-line arguments to the current RoseDrawing entry point.
 */
@Deprecated
public final class RoseDrawing_2 {

    private RoseDrawing_2() {
        // Utility class
    }

    public static void main(String[] args) throws IOException {
        RoseDrawing.main(args);
    }
}
