package com.tonic.fixtures;

public class NullArrayPhi {
    public static String[] build(String[] lines) {
        String[] pages = null;
        for (String line : lines) {
            if (line.equals("common")) {
                pages = new String[3];
            } else if (line.equals("page")) {
                pages[0] = line;
            }
        }
        return pages;
    }
}
