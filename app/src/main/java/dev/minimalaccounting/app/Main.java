package dev.minimalaccounting.app;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int status = new AccountingApplication().run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }
}
