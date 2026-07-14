package com.tonic.fixtures;

public class UninitThisRepro {
    int x;

    public UninitThisRepro(boolean b) {
        super();
        if (b) {
        }
        this.x = 1;
    }
}
