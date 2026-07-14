package com.tonic.fixtures;

public class LoopCarriedBooleanFlag {
    public static String forMacro(String code) {
        boolean captured = false;
        for (String l : code.split("\n")) {
            if (!captured) {
                if (l.startsWith("#")) {
                    captured = true;
                }
            }
        }
        if (captured) {
            code = forMacro(code);
        }
        return code;
    }
}
