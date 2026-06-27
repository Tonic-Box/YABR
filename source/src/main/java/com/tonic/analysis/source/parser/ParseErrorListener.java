package com.tonic.analysis.source.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@FunctionalInterface
public interface ParseErrorListener {

    void onError(ParseException error);

    static ParseErrorListener throwing() {
        return error -> { throw error; };
    }

    static ParseErrorListener collecting(List<ParseException> errors) {
        return errors::add;
    }

    static CollectingErrorListener collecting() {
        return new CollectingErrorListener();
    }

    static ParseErrorListener logging(Consumer<String> logger) {
        return error -> logger.accept(error.getFormattedMessage());
    }

    static ParseErrorListener silent() {
        return error -> {};
    }

    default ParseErrorListener andThen(ParseErrorListener other) {
        return error -> {
            this.onError(error);
            other.onError(error);
        };
    }

    class CollectingErrorListener implements ParseErrorListener {
        private final List<ParseException> errors = new ArrayList<>();

        @Override
        public void onError(ParseException error) {
            errors.add(error);
        }

        public List<ParseException> getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int errorCount() {
            return errors.size();
        }

        public void clear() {
            errors.clear();
        }

        public String formatAll() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(errors.get(i).getFormattedMessage());
            }
            return sb.toString();
        }
    }
}
