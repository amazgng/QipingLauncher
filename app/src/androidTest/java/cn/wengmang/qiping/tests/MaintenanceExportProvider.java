package cn.wengmang.qiping.tests;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Disposable emulator fixture: only one fixed file can be addressed. */
public final class MaintenanceExportProvider extends ContentProvider {
    private File target(Uri uri)throws FileNotFoundException {
        if(!"/backup".equals(uri.getPath()))throw new FileNotFoundException();
        return new File(getContext().getFilesDir(),"maintenance-export.qiping");
    }
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode)&&!"w".equals(mode))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(target(uri),"r".equals(mode)?ParcelFileDescriptor.MODE_READ_ONLY:ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE);
    }
    @Override public String getType(Uri uri){return "application/octet-stream";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String sort){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){try{return target(u).delete()?1:0;}catch(FileNotFoundException e){return 0;}}
}
