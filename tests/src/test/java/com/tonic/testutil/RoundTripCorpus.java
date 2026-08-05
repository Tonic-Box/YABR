package com.tonic.testutil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The compiled demo application the round-trip tests read. It lives in a sibling project rather
 * than this repo, so the location is declared once here instead of being repeated in every test
 * that walks it, and can be pointed elsewhere with {@code -Dyabr.demo.classes=<dir>}.
 *
 * Tests must treat an absent directory as an explicit skip ({@code assumeTrue(available())}) - a
 * silent pass would let the corpus quietly stop being exercised.
 */
public final class RoundTripCorpus
{

    private RoundTripCorpus() {}

    /**
     * The directory holding the demo application's compiled classes.
     */
    public static final String DIR = System.getProperty("yabr.demo.classes",
            "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main");

    public static Path path()
    {
        return Paths.get(DIR);
    }

    public static boolean available()
    {
        return Files.isDirectory(path());
    }
}
