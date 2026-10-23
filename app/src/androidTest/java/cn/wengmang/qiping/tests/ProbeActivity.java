package cn.wengmang.qiping.tests;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Exists only inside the optional instrumentation package, never the delivered launcher. */
public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView text=new TextView(this);text.setText(android.content.Intent.ACTION_VIEW.equals(getIntent().getAction())?"Test store handoff only. No downloads or installation. Press Back to return.":"Qiping test application. Press Back to return.");text.setTextSize(24);setContentView(text);}
}
