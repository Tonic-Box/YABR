package com.tonic.type;

public interface AccessFlags {

    int ACC_PUBLIC = 0x0001;
    int ACC_PRIVATE = 0x0002;
    int ACC_PROTECTED = 0x0004;
    int ACC_STATIC = 0x0008;
    int ACC_FINAL = 0x0010;
    int ACC_SUPER = 0x0020;
    int ACC_SYNCHRONIZED = 0x0020;
    int ACC_VOLATILE = 0x0040;
    int ACC_BRIDGE = 0x0040;
    int ACC_TRANSIENT = 0x0080;
    int ACC_VARARGS = 0x0080;
    int ACC_NATIVE = 0x0100;
    int ACC_INTERFACE = 0x0200;
    int ACC_ABSTRACT = 0x0400;
    int ACC_STRICT = 0x0800;
    int ACC_SYNTHETIC = 0x1000;
    int ACC_ANNOTATION = 0x2000;
    int ACC_ENUM = 0x4000;
    int ACC_MANDATED = 0x8000;
    int ACC_MODULE = 0x8000;

    int T_BOOLEAN = 4;
    int T_CHAR = 5;
    int T_FLOAT = 6;
    int T_DOUBLE = 7;
    int T_BYTE = 8;
    int T_SHORT = 9;
    int T_INT = 10;
    int T_LONG = 11;

    int V1_1 = 45;
    int V1_2 = 46;
    int V1_3 = 47;
    int V1_4 = 48;
    int V1_5 = 49;
    int V1_6 = 50;
    int V1_7 = 51;
    int V1_8 = 52;
    int V9 = 53;
    int V10 = 54;
    int V11 = 55;
    int V12 = 56;
    int V13 = 57;
    int V14 = 58;
    int V15 = 59;
    int V16 = 60;
    int V17 = 61;
    int V18 = 62;
    int V19 = 63;
    int V20 = 64;
    int V21 = 65;
    int V22 = 66;
    int V23 = 67;

    int ILOAD = 0x15;
    int LLOAD = 0x16;
    int FLOAD = 0x17;
    int DLOAD = 0x18;
    int ALOAD = 0x19;

    int ISTORE = 0x36;
    int LSTORE = 0x37;
    int FSTORE = 0x38;
    int DSTORE = 0x39;
    int ASTORE = 0x3A;

    int IRETURN = 0xAC;
    int LRETURN = 0xAD;
    int FRETURN = 0xAE;
    int DRETURN = 0xAF;
    int ARETURN = 0xB0;
    int RETURN = 0xB1;
}
