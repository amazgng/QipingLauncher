package cn.wengmang.qiping;

public final class GridNavigationTest {
    private static int checks;
    private static void eq(int actual,int expected,String text){checks++;if(actual!=expected)throw new AssertionError(text+": "+actual+" != "+expected);}
    public static void main(String[] args) {
        eq(GridNavigation.horizontal(0,0,true),-1,"empty list");
        eq(GridNavigation.horizontal(-1,20,true),-1,"no focus");
        eq(GridNavigation.horizontal(20,20,true),-1,"stale focus after uninstall");
        eq(GridNavigation.horizontal(4,25,true),5,"ordinary right movement");
        eq(GridNavigation.horizontal(5,25,true),12,"first row crosses to next page");
        eq(GridNavigation.horizontal(11,25,true),18,"second row crosses to same row");
        eq(GridNavigation.horizontal(12,25,false),5,"return to previous first row edge");
        eq(GridNavigation.horizontal(18,25,false),11,"return to previous second row edge");
        eq(GridNavigation.horizontal(23,25,true),24,"one-item final page falls back to first row");
        eq(GridNavigation.horizontal(24,25,true),24,"rightmost end stays in place");
        eq(GridNavigation.horizontal(24,25,false),17,"short final page returns to its first row");
        eq(GridNavigation.horizontal(0,25,false),GridNavigation.BEFORE_FIRST,"first page left edge signals parent");
        eq(GridNavigation.horizontal(6,25,false),GridNavigation.BEFORE_FIRST,"second row left edge signals parent");
        eq(GridNavigation.horizontal(6,7,true),6,"short single page never focuses an empty slot");
        eq(GridNavigation.pageTarget(1,1,13,true),12,"swipe to one-item final page");
        eq(GridNavigation.pageTarget(1,1,19,true),18,"swipe preserves second row when available");
        eq(GridNavigation.pageTarget(0,1,25,false),11,"swipe back chooses row end");
        eq(GridNavigation.pageTarget(3,0,25,true),-1,"swipe beyond end ignored");
        for(int count=1;count<=65;count++)for(int index=0;index<count;index++)for(boolean right:new boolean[]{false,true}) {
            int target=GridNavigation.horizontal(index,count,right);checks++;
            if(target!=GridNavigation.BEFORE_FIRST&&(target<0||target>=count))throw new AssertionError("focus escapes populated tiles");
            if(target>=0&&Math.abs(target/GridNavigation.PAGE_SIZE-index/GridNavigation.PAGE_SIZE)>1)throw new AssertionError("skipped a page");
        }
        System.out.println("GridNavigationTest: "+checks+" assertions passed");
    }
}
