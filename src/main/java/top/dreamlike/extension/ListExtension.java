package top.dreamlike.extension;

import java.util.List;
import java.util.function.Predicate;

public class ListExtension {


    public static <T> T findAny(List<T> list, Predicate<T> predicate) {
        for (T t : list) {
            if (predicate.test(t)) {
                return t;
            }
        }
        return null;
    }
}
