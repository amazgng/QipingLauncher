package cn.wengmang.qiping;
import java.util.Arrays;
import java.util.Collections;

public final class FavoritesTest {
    private static void check(boolean value,String message) {
        if(!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Favorites clean=new Favorites(Collections.<String>emptyList());
        check(clean.firstEmpty()==0,"fresh launcher must allow adding first app");
        for(int i=0;i<Favorites.SIZE;i++)clean.put(i,"package."+i);
        check(clean.firstEmpty()==-1,"full home must require choosing a replacement");
        clean.put(2,"package.5");
        check(clean.get(2).equals("package.5")&&clean.get(5).equals(""),"moving an existing app must not duplicate it");
        clean.move(2,3);
        check(clean.get(3).equals("package.5")&&clean.get(2).equals("package.3"),"reorder must preserve the displaced app");
        Favorites restored=new Favorites(clean.snapshot());
        check(restored.snapshot().equals(clean.snapshot()),"restart must preserve exact slot positions");
        restored.clear(3);check(restored.get(3).equals(""),"remove must affect only selected slot");
        check(restored.get(2).equals("package.3"),"remove must not shift other slots");
        Favorites corrupt=new Favorites(Arrays.asList("a","a",null,"b"));
        check(corrupt.get(0).equals("a")&&corrupt.get(1).equals("")&&corrupt.get(2).equals("")&&corrupt.get(3).equals("b"),"duplicate and null stored values must recover safely");
        System.out.println("PASS: favorites add, full capacity, replace, deduplicate, reorder, restart, remove, corrupt data");
    }
}
