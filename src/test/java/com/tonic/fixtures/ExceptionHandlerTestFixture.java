package com.tonic.fixtures;

public class ExceptionHandlerTestFixture {

    public static int simpleTryCatch(int value) {
        try {
            if (value < 0) {
                throw new RuntimeException();
            }
            return value * 2;
        } catch (Exception e) {
            return -1;
        }
    }

    public static int simpleThrower(int value) {
        if (value < 0) {
            throw new RuntimeException();
        }
        return value * 2;
    }

    public static int catchAllHandler(int value) {
        try {
            if (value < 0) {
                throw new RuntimeException();
            }
            return value;
        } catch (Throwable t) {
            return -999;
        }
    }

    public static int nestedTryCatchSimple(int outer, int inner) {
        try {
            try {
                if (inner < 0) {
                    throw new RuntimeException();
                }
                return outer + inner;
            } catch (Exception e) {
                return outer - 1;
            }
        } catch (Throwable t) {
            return -999;
        }
    }

    public static int multipleHandlers(int selector) {
        try {
            switch (selector) {
                case 1:
                    throw new IllegalArgumentException();
                case 2:
                    throw new IllegalStateException();
                default:
                    return selector;
            }
        } catch (IllegalArgumentException e) {
            return 100;
        } catch (IllegalStateException e) {
            return 200;
        } catch (Exception e) {
            return 300;
        }
    }

    public static int returnFromTry(int value) {
        try {
            return value * 2;
        } catch (Exception e) {
            return -1;
        }
    }

    public static int returnFromCatch(int value) {
        try {
            if (value < 0) {
                throw new RuntimeException();
            }
            return value;
        } catch (Exception e) {
            return value * -1;
        }
    }

    public static int callerWithHandler(int value) {
        try {
            return calleeThrows(value);
        } catch (Exception e) {
            return -100;
        }
    }

    public static int calleeThrows(int value) {
        if (value < 0) {
            throw new RuntimeException();
        }
        return value + 10;
    }

    public static int deepCallChain(int value) {
        try {
            return level1(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int level1(int value) {
        return level2(value);
    }

    private static int level2(int value) {
        return level3(value);
    }

    private static int level3(int value) {
        if (value < 0) {
            throw new RuntimeException();
        }
        return value * 3;
    }

    public static int rethrowException(int value) {
        try {
            try {
                if (value < 0) {
                    throw new RuntimeException();
                }
                return value;
            } catch (RuntimeException e) {
                throw e;
            }
        } catch (Exception e) {
            return -2;
        }
    }

    public static int noHandler(int value) {
        if (value < 0) {
            throw new RuntimeException();
        }
        return value;
    }

    public static int simpleCalculation(int a, int b) {
        return a + b;
    }
}
