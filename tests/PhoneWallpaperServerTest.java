package cn.wengmang.qiping;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real socket tests: framing, session authentication, upload state, concurrency and shutdown. */
public final class PhoneWallpaperServerTest {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static Map<String,byte[]> assets(){Map<String,byte[]> map=new HashMap<String,byte[]>();map.put("/","LOCAL PAGE".getBytes(StandardCharsets.UTF_8));return map;}
    static String token(PhoneWallpaperServer server){return server.qrAddress().split("#")[1];}
    static String request(PhoneWallpaperServer server,String method,String path,String extra,byte[] body)throws Exception{
        URL url=new URL(server.address());try(Socket socket=new Socket(url.getHost(),url.getPort())){socket.setSoTimeout(5000);OutputStream out=socket.getOutputStream();String headers=method+" "+path+" HTTP/1.1\r\nHost: "+url.getAuthority()+"\r\n"+extra+(body==null?"":"Content-Length: "+body.length+"\r\n")+"Connection: close\r\n\r\n";out.write(headers.getBytes(StandardCharsets.US_ASCII));if(body!=null)out.write(body);out.flush();ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;while((n=socket.getInputStream().read(buffer))!=-1)result.write(buffer,0,n);return new String(result.toByteArray(),StandardCharsets.UTF_8);}
    }
    static String auth(PhoneWallpaperServer server){return "X-Qiping-Token: "+token(server)+"\r\n";}
    static void code(String response,int code,String label){check(response.startsWith("HTTP/1.1 "+code+" "),label+": "+response.substring(0,Math.min(130,response.length())));}
    public static void main(String[] args)throws Exception {
        final AtomicInteger received=new AtomicInteger();final AtomicBoolean hold=new AtomicBoolean();final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        PhoneWallpaperServer.Receiver receiver=new PhoneWallpaperServer.Receiver(){public void receive(int scene,InputStream input,PhoneWallpaperServer.Cancellation cancellation)throws IOException{
            if(hold.get()){entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException("cancelled");}}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[128];int n;while((n=input.read(buffer))!=-1){if(cancellation.cancelled())throw new IOException("cancelled");bytes.write(buffer,0,n);}if(bytes.size()<4)throw new IOException("invalid fixture");received.incrementAndGet();
        }};
        PhoneWallpaperServer server=new PhoneWallpaperServer(InetAddress.getByName("127.0.0.1"),3,assets(),receiver,null);server.start();byte[] image={1,2,3,4,5};
        try{
            check(token(server).matches("[a-f0-9]{32}"),"128-bit QR token");check(server.pairCode().matches("[0-9]{6}"),"six digit manual code");
            String page=request(server,"GET","/","",null);code(page,200,"local page");check(page.contains("Cache-Control: no-store")&&page.contains("frame-ancestors 'none'")&&!page.contains(token(server)),"page does not expose token; browser isolation headers");
            code(request(server,"GET","/status","",null),401,"status needs pairing");code(request(server,"GET","/status",auth(server),null),200,"paired status");
            check(request(server,"GET","/status",auth(server),null).contains("\"defaultScene\":3"),"TV category becomes phone default");
            code(request(server,"POST","/pair","",server.pairCode().getBytes(StandardCharsets.US_ASCII)),200,"manual pairing");
            code(request(server,"POST","/upload?scene=0",auth(server)+"Origin: http://other.invalid\r\nContent-Type: image/png\r\n",image),403,"foreign origin blocked");
            code(request(server,"GET","/status",auth(server)+"Sec-Fetch-Site: cross-site\r\n",null),403,"cross-site blocked");
            code(request(server,"GET","/../../secret",auth(server),null),404,"no filesystem routing");
            check(request(server,"GET","/status",auth(server)+"Host: evil.invalid\r\n",null).isEmpty(),"duplicate Host rejected");
            code(request(server,"POST","/upload?scene=6",auth(server)+"Content-Type: image/png\r\n",image),404,"scene bounded");
            code(request(server,"POST","/upload?scene=0",auth(server)+"Content-Type: text/html\r\n",image),415,"non-image type blocked");
            code(request(server,"POST","/upload?scene=0",auth(server)+"Content-Type: image/png\r\n",new byte[0]),400,"empty upload blocked");
            code(request(server,"POST","/upload?scene=0",auth(server)+"Content-Type: image/png\r\nContent-Length: 20971521\r\n",null),413,"20 MB bound before reading body");
            check(request(server,"POST","/upload?scene=0",auth(server)+"Transfer-Encoding: chunked\r\n",null).isEmpty(),"ambiguous framing refused");
            code(request(server,"POST","/upload?scene=0",auth(server)+"Content-Type: image/png\r\n",new byte[]{1}),422,"receiver rejection does not create pending image");check("error".equals(server.state()),"retry possible after bad image");
            code(request(server,"POST","/upload?scene=4",auth(server)+"Content-Type: image/png\r\n",image),200,"valid upload");check("pending".equals(server.state())&&server.scene()==4&&received.get()==1,"upload waits for TV confirmation");
            code(request(server,"POST","/upload?scene=1",auth(server)+"Content-Type: image/png\r\n",new byte[128*1024]),409,"rejected larger body retains readable response");
            code(request(server,"POST","/upload?scene=1",auth(server)+"Content-Type: image/png\r\n",image),409,"pending upload cannot be overwritten");check(server.beginApply()&&!server.beginApply(),"single TV application");server.applied(true);check("applied".equals(server.state()),"confirmed status");
            code(request(server,"POST","/upload?scene=5",auth(server)+"Content-Type: image/png\r\n",image),200,"second wallpaper in same session");check(server.reject()&&!server.reject()&&"rejected".equals(server.state()),"TV discard is idempotent");
            hold.set(true);final PhoneWallpaperServer current=server;final Throwable[] failure={null};Thread first=new Thread(new Runnable(){public void run(){try{code(request(current,"POST","/upload?scene=2",auth(current)+"Content-Type: image/png\r\n",image),200,"first concurrent upload");}catch(Throwable t){failure[0]=t;}}});first.start();check(entered.await(2,TimeUnit.SECONDS),"receiving state entered");
            code(request(server,"POST","/upload?scene=1",auth(server)+"Content-Type: image/png\r\n",image),409,"concurrent upload bounded");release.countDown();first.join(5000);check(!first.isAlive()&&failure[0]==null&&received.get()==3,"only one concurrent receive");
            String wrong=server.pairCode().equals("000000")?"111111":"000000";
            for(int i=0;i<5;i++)code(request(server,"POST","/pair","",wrong.getBytes(StandardCharsets.US_ASCII)),403,"bad pair code "+i);
            code(request(server,"POST","/pair","",server.pairCode().getBytes(StandardCharsets.US_ASCII)),429,"manual pairing attempts bounded");
        }finally{server.close();server.awaitClosed();}
        check(!server.isRunning(),"server closed");boolean refused=false;try{request(server,"GET","/","",null);}catch(IOException e){refused=true;}check(refused,"port closed after exit");
        PhoneWallpaperServer expiring=new PhoneWallpaperServer(InetAddress.getByName("127.0.0.1"),0,assets(),receiver,null,120);expiring.start();Thread.sleep(180);check(!expiring.isRunning(),"session expiration enforced");expiring.close();expiring.awaitClosed();
        PhoneWallpaperServer next=new PhoneWallpaperServer(InetAddress.getByName("127.0.0.1"),0,assets(),receiver,null);next.start();try{check(!token(next).equals(token(server)),"new session rotates secret");code(request(next,"GET","/status",auth(server),null),401,"old QR cannot authorize new session");}finally{next.close();next.awaitClosed();}
        System.out.println("PASS: PhoneWallpaperServer "+checks+" socket/security/state checks");
    }
}
