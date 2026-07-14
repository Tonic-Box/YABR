package com.tonic.fixtures;

public class ValueAsConditionReturned {
    boolean base(int s) {
        return s >= 0;
    }

    public boolean check(int s, int[] arr) {
        boolean result = base(s);
        if (result) {
            for (int c : arr) {
                if (c < 0) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }
}
