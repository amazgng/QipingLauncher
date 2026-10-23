package cn.wengmang.qiping;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** A bounded, single-session LAN receiver. No discovery, external requests or background service. */
final class PhoneWallpaperServer implements Closeable {
    static final int MAX_BYTES=20*1024*1024;
    static final long SESSION_MS=10*60*1000L;
    interface Cancellation { boolean cancelled(); }
    interface Receiver { void receive(int scene,InputStream body,Cancellation cancellation)throws IOException; }
    interface Listener { void changed(); }
    private final Receiver receiver;
    private final Listener listener;
    private final Map<String,byte[]> assets;
    private final ServerSocket socket;
    private final ServerSocket loopbackSocket;
    private final Set<Socket> clients=Collections.synchronizedSet(new HashSet<Socket>());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(3,3,15,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(3));
    private final String token,pairCode,authority,origin;
    private final long deadline;
    private final int defaultScene;
    private volatile boolean running=true;
    private volatile String state="ready",message="等待手机上传";
    private volatile int scene=-1,progress;
    private int badCodes;

    PhoneWallpaperServer(InetAddress address,int defaultScene,Map<String,byte[]> assets,Receiver receiver,Listener listener)throws IOException {
        this(address,defaultScene,assets,receiver,listener,SESSION_MS);
    }
    PhoneWallpaperServer(InetAddress address,int defaultScene,Map<String,byte[]> assets,Receiver receiver,Listener listener,long duration)throws IOException {
        if(!(address instanceof Inet4Address)||(!address.isSiteLocalAddress()&&!address.isLoopbackAddress()))throw new IOException("请连接家庭 Wi-Fi 或有线局域网");
        this.defaultScene=Math.max(0,Math.min(5,defaultScene));this.assets=assets;this.receiver=receiver;this.listener=listener;
        SecureRandom random=new SecureRandom();byte[] bytes=new byte[16];random.nextBytes(bytes);StringBuilder secret=new StringBuilder();
        for(byte b:bytes)secret.append(String.format(Locale.US,"%02x",b&255));token=secret.toString();pairCode=String.format(Locale.US,"%06d",random.nextInt(1000000));
        socket=new ServerSocket();socket.setReuseAddress(false);socket.bind(new InetSocketAddress(address,0),6);socket.setSoTimeout(1000);
        // A loopback-only companion allows Android Studio's adb forward without widening the LAN bind.
        ServerSocket local=null;
        if(!address.isLoopbackAddress())try{local=new ServerSocket();local.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),socket.getLocalPort()),3);local.setSoTimeout(1000);}catch(IOException unavailable){if(local!=null)try{local.close();}catch(IOException ignored){}local=null;}
        loopbackSocket=local;
        authority=address.getHostAddress()+":"+socket.getLocalPort();origin="http://"+authority;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(duration);
    }
    void start(){listen(socket);if(loopbackSocket!=null)listen(loopbackSocket);}
    private void listen(final ServerSocket endpoint){Thread thread=new Thread(new Runnable(){public void run(){accept(endpoint);}},"qiping-wallpaper-listener");thread.setDaemon(true);thread.start();}
    String address(){return origin+"/";}
    String qrAddress(){return address()+"#"+token;}
    String pairCode(){return pairCode;}
    String state(){return state;}
    String message(){return message;}
    int scene(){return scene;}
    int progress(){return progress;}
    long remainingMillis(){return Math.max(0,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));}
    boolean isRunning(){return running&&remainingMillis()>0;}
    synchronized boolean beginApply(){if(!isRunning()||!"pending".equals(state))return false;state="applying";message="正在应用壁纸";changed();return true;}
    synchronized void applied(boolean success){if(!running)return;state=success?"applied":"error";message=success?"电视已应用壁纸，可以继续上传":"保存失败，原壁纸已保留，请重新上传";changed();}
    synchronized boolean reject(){if(!isRunning()||!"pending".equals(state))return false;state="rejected";message="电视已放弃这张图片，请重新选择";changed();return true;}
    void stop(String reason){synchronized(this){if(!running)return;running=false;state="closed";message=reason;}try{socket.close();}catch(IOException ignored){}if(loopbackSocket!=null)try{loopbackSocket.close();}catch(IOException ignored){}synchronized(clients){for(Socket client:clients)try{client.close();}catch(IOException ignored){}}workers.shutdownNow();changed();}
    @Override public void close(){stop("连接已结束，请在电视上重新开启");}
    void awaitClosed() {try{workers.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
    private void changed(){if(listener!=null)listener.changed();}
    private void accept(ServerSocket endpoint){
        while(running){
            if(remainingMillis()==0){stop("连接已到期，请重新连接");break;}
            try{final Socket client=endpoint.accept();client.setSoTimeout(5000);clients.add(client);
                try{workers.execute(new Runnable(){public void run(){serve(client);}});}catch(RejectedExecutionException full){clients.remove(client);client.close();}
            }catch(SocketTimeoutException ignored){}catch(IOException error){if(running)stop("连接中断，请重新连接");}
        }
    }
    private void serve(Socket client){
        try{
            BufferedInputStream input=new BufferedInputStream(client.getInputStream(),16384);OutputStream out=client.getOutputStream();
            Request request=Request.read(input);
            if(!isRunning()){respond(out,410,"application/json",jsonError("连接已到期，请在电视上重新开启"));return;}
            String host=request.headers.get("host");boolean local=client.getLocalAddress().isLoopbackAddress();
            boolean validHost=authority.equals(host)||(local&&(("127.0.0.1:"+socket.getLocalPort()).equals(host)||("localhost:"+socket.getLocalPort()).equals(host)));
            if(!validHost){respond(out,403,"application/json",jsonError("地址不匹配"));return;}
            String suppliedOrigin=request.headers.get("origin"),site=request.headers.get("sec-fetch-site");
            if((suppliedOrigin!=null&&!("http://"+host).equals(suppliedOrigin))||"cross-site".equals(site)){respond(out,403,"application/json",jsonError("请直接打开电视显示的地址"));return;}
            if("GET".equals(request.method)&&assets.containsKey(request.path)){
                String type=request.path.endsWith(".js")?"application/javascript; charset=utf-8":request.path.endsWith(".css")?"text/css; charset=utf-8":"text/html; charset=utf-8";
                respond(out,200,type,assets.get(request.path));return;
            }
            if("POST".equals(request.method)&&"/pair".equals(request.path)){
                int length=request.length();if(length!=6){respond(out,400,"application/json",jsonError("请输入电视上的 6 位配对码"));return;}
                String code=new String(readExact(input,6),StandardCharsets.US_ASCII);
                synchronized(this){
                    if(badCodes>=5){respond(out,429,"application/json",jsonError("配对次数过多，请在电视上重新连接"));return;}
                    if(!constantEquals(pairCode,code)){badCodes++;respond(out,403,"application/json",jsonError("配对码不正确，请查看电视"));return;}
                }
                respond(out,200,"application/json",bytes("{\"token\":\""+token+"\"}"));return;
            }
            if(!constantEquals(token,request.headers.get("x-qiping-token"))){respond(out,401,"application/json",jsonError("请扫码或输入电视上的配对码"));return;}
            if("GET".equals(request.method)&&"/status".equals(request.path)){respond(out,200,"application/json",statusJson());return;}
            if("POST".equals(request.method)&&request.path.matches("/upload\\?scene=[0-5]")){
                int length=request.length();if(length<=0){respond(out,400,"application/json",jsonError("请选择一张图片"));return;}
                if(length>MAX_BYTES){respond(out,413,"application/json",jsonError("图片超过 20 MB，请换一张较小的图片"));return;}
                String type=request.headers.get("content-type");if(type==null||(!type.startsWith("image/")&&!type.equals("application/octet-stream"))){respond(out,415,"application/json",jsonError("请选择图片文件"));return;}
                synchronized(this){
                    if("receiving".equals(state)||"pending".equals(state)||"applying".equals(state)){respond(out,409,"application/json",jsonError("请先在电视上处理上一张图片"));return;}
                    if(!isRunning()){respond(out,410,"application/json",jsonError("连接已结束"));return;}
                    scene=request.path.charAt(request.path.length()-1)-'0';state="receiving";message="正在接收图片";progress=0;
                }
                changed();
                try{
                    final long readDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
                    LimitedBody body=new LimitedBody(input,length,readDeadline);
                    receiver.receive(scene,body,new Cancellation(){public boolean cancelled(){return !isRunning()||Thread.currentThread().isInterrupted();}});
                    if(body.remaining!=0)throw new IOException("上传未完成，请重试");
                    synchronized(this){if(!isRunning())throw new IOException("连接已结束");state="pending";message="图片已收到，请在电视上确认应用";progress=100;}
                    changed();respond(out,200,"application/json",statusJson());
                }catch(Exception error){
                    synchronized(this){if(running){state="error";message=error instanceof IOException&&error.getMessage()!=null?error.getMessage():"图片无法读取，请选择有效图片";}}
                    changed();respond(out,422,"application/json",jsonError(message));
                }
                return;
            }
            respond(out,404,"application/json",jsonError("页面不存在"));
        }catch(Exception ignored){/* Bad, abandoned or slow requests close without keeping a thread alive. */}
        finally{
            // Send the completed HTTP response before closing a rejected upload. A close with
            // unread TCP data can reset the connection and hide the useful 409/413 response.
            try{client.shutdownOutput();if(running){client.setSoTimeout(120);InputStream rest=client.getInputStream();byte[] discard=new byte[8192];int count=0,n;long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(300);while(count<=MAX_BYTES&&System.nanoTime()<until&&(n=rest.read(discard))!=-1)count+=n;}}catch(IOException ignored){}
            clients.remove(client);try{client.close();}catch(IOException ignored){}
        }
    }
    private synchronized byte[] statusJson(){return bytes("{\"state\":\""+state+"\",\"message\":\""+escape(message)+"\",\"scene\":"+scene+",\"defaultScene\":"+defaultScene+",\"progress\":"+progress+",\"remaining\":"+(remainingMillis()/1000)+"}");}
    private final class LimitedBody extends InputStream {
        final InputStream input;final int total;final long limit;int remaining;
        LimitedBody(InputStream input,int length,long limit){this.input=input;total=remaining=length;this.limit=limit;}
        @Override public int read()throws IOException {byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}
        @Override public int read(byte[] b,int off,int len)throws IOException {
            if(len==0)return 0;if(!isRunning()||System.nanoTime()>limit)throw new IOException("接收超时，请重新上传");if(remaining==0)return -1;
            int n=input.read(b,off,Math.min(len,remaining));if(n<0)throw new IOException("上传中断，请重新选择图片");remaining-=n;int value=(int)((total-(long)remaining)*100/total);
            if(value/10!=progress/10){progress=value;changed();}return n;
        }
    }
    private static final class Request {
        String method,path;final Map<String,String> headers=new HashMap<String,String>();
        static Request read(InputStream input)throws IOException {
            ByteArrayOutputStream data=new ByteArrayOutputStream();int tail=0;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(data.size()<8192){if(System.nanoTime()>until)throw new IOException("header timeout");int b=input.read();if(b<0)throw new EOFException();data.write(b);tail=(tail<<8)|b;if(tail==0x0d0a0d0a)break;}
            if(tail!=0x0d0a0d0a)throw new IOException("header too large");String[] lines=new String(data.toByteArray(),StandardCharsets.US_ASCII).split("\r\n");
            String[] start=lines[0].split(" ",-1);if(start.length!=3||start[1].length()>1024||!(start[2].equals("HTTP/1.1")||start[2].equals("HTTP/1.0")))throw new IOException("bad request");
            Request result=new Request();result.method=start[0];result.path=start[1];
            for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon<=0||lines[i].startsWith(" ")||lines[i].startsWith("\t"))throw new IOException("bad header");String name=lines[i].substring(0,colon).toLowerCase(Locale.US),value=lines[i].substring(colon+1).trim();if(result.headers.put(name,value)!=null)throw new IOException("duplicate header");}
            if(result.headers.containsKey("transfer-encoding")||result.headers.containsKey("expect"))throw new IOException("unsupported framing");return result;
        }
        int length()throws IOException {String value=headers.get("content-length");if(value==null||!value.matches("[0-9]{1,10}"))throw new IOException("invalid length");try{long size=Long.parseLong(value);return size>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)size;}catch(NumberFormatException e){throw new IOException("invalid length");}}
    }
    private static byte[] readExact(InputStream input,int length)throws IOException {byte[] result=new byte[length];int offset=0;while(offset<length){int n=input.read(result,offset,length-offset);if(n<0)throw new EOFException();offset+=n;}return result;}
    private static boolean constantEquals(String a,String b){return b!=null&&MessageDigest.isEqual(bytes(a),bytes(b));}
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static byte[] jsonError(String message){return bytes("{\"message\":\""+escape(message)+"\"}");}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\r"," ").replace("\n"," ").replace("\t"," ");}
    private static void respond(OutputStream out,int status,String type,byte[] data)throws IOException {
        String headers="HTTP/1.1 "+status+" "+(status==200?"OK":"Error")+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; img-src blob:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'\r\n\r\n";
        out.write(bytes(headers));out.write(data);out.flush();
    }
}
