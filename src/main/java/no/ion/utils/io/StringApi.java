package no.ion.utils.io;

public interface StringApi extends CharSequence {
    int indexOf(String str);
    int indexOf(String str, int fromIndex);
    int lastIndexOf(String str);

    static StringApi from(String string) {
        return new StringApi() {
            @Override
            public int indexOf(String str) { return string.indexOf(str); }

            @Override
            public int indexOf(String str, int fromIndex) { return string.indexOf(str, fromIndex); }

            @Override
            public int lastIndexOf(String str) { return string.lastIndexOf(str); }

            @Override
            public int length() { return string.length(); }

            @Override
            public char charAt(int index) { return string.charAt(index); }

            @Override
            public CharSequence subSequence(int start, int end) { return string.subSequence(start, end); }
        };
    }

    static StringApi from(StringBuilder stringBuilder) {
        return new StringApi() {
            @Override
            public int indexOf(String str) { return stringBuilder.indexOf(str); }

            @Override
            public int indexOf(String str, int fromIndex) { return stringBuilder.indexOf(str, fromIndex); }

            @Override
            public int lastIndexOf(String str) { return stringBuilder.lastIndexOf(str); }

            @Override
            public int length() { return stringBuilder.length(); }

            @Override
            public char charAt(int index) { return stringBuilder.charAt(index); }

            @Override
            public CharSequence subSequence(int start, int end) { return stringBuilder.subSequence(start, end); }
        };
    }
}
