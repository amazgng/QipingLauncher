package cn.wengmang.qiping;

/** Pure navigation policy: horizontal page changes preserve the row, including a short last page. */
final class GridNavigation {
    static final int COLUMNS=6, PAGE_SIZE=12, BEFORE_FIRST=-2;
    static int horizontal(int index,int count,boolean right) {
        if(count<=0||index<0||index>=count)return -1;
        int page=index/PAGE_SIZE,local=index%PAGE_SIZE,row=local/COLUMNS,column=local%COLUMNS;
        int first=page*PAGE_SIZE+row*COLUMNS,last=Math.min(first+COLUMNS,count)-1;
        if(right&&index<last)return index+1;
        if(!right&&column>0)return index-1;
        int nextPage=page+(right?1:-1);
        if(nextPage<0)return BEFORE_FIRST;
        if(nextPage*PAGE_SIZE>=count)return index;
        int rowFirst=nextPage*PAGE_SIZE+row*COLUMNS;
        if(rowFirst>=count)rowFirst=nextPage*PAGE_SIZE;
        return right?rowFirst:Math.min(rowFirst+COLUMNS,count)-1;
    }
    static int pageTarget(int page,int row,int count,boolean right) {
        if(count<=0)return -1;
        int first=page*PAGE_SIZE;
        if(first<0||first>=count)return -1;
        int rowFirst=first+Math.max(0,Math.min(1,row))*COLUMNS;
        if(rowFirst>=count)rowFirst=first;
        return right?rowFirst:Math.min(rowFirst+COLUMNS,count)-1;
    }
}
