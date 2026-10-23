package cn.wengmang.qiping;

import java.util.ArrayList;
import java.util.List;

/** Fixed slots keep user ordering intact even when an app is temporarily missing. */
public final class Favorites {
    public static final int SIZE = 6;
    private final String[] slots = new String[SIZE];

    public Favorites(List<String> saved) {
        for (int i = 0; i < SIZE; i++) {
            String value = i < saved.size() ? saved.get(i) : "";
            if (value == null || contains(value)) value = "";
            slots[i] = value;
        }
    }

    public String get(int index) { return slots[index]; }
    public boolean contains(String pkg) { return indexOf(pkg) >= 0; }
    public int indexOf(String pkg) {
        if (pkg == null || pkg.length() == 0) return -1;
        for (int i = 0; i < SIZE; i++) if (pkg.equals(slots[i])) return i;
        return -1;
    }
    public int firstEmpty() {
        for (int i = 0; i < SIZE; i++) if (slots[i] == null || slots[i].length() == 0) return i;
        return -1;
    }
    public void put(int index, String pkg) {
        if (index < 0 || index >= SIZE || pkg == null) throw new IllegalArgumentException();
        int previous = indexOf(pkg);
        if (previous >= 0 && previous != index) slots[previous] = "";
        slots[index] = pkg;
    }
    public void clear(int index) { slots[index] = ""; }
    public void move(int from, int to) {
        if (from < 0 || from >= SIZE || to < 0 || to >= SIZE) return;
        String value = slots[from]; slots[from] = slots[to]; slots[to] = value;
    }
    public List<String> snapshot() {
        ArrayList<String> out = new ArrayList<String>();
        for (String slot : slots) out.add(slot == null ? "" : slot);
        return out;
    }
}
