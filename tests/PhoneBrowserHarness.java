package cn.wengmang.qiping;
import java.io.*;import java.net.*;import java.nio.file.*;import java.util.*;
/** Host-only browser fixture; stdin simulates the television's confirm/reject buttons. */
public final class PhoneBrowserHarness {
    public static void main(String[] args)throws Exception{
        Map<String,byte[]> assets=new HashMap<String,byte[]>();for(String name:new String[]{"index.html","crop.js","app.js","style.css"})assets.put(name.equals("index.html")?"/":"/"+name,Files.readAllBytes(Paths.get(args[0],name)));
        final String destination=args.length>1?args[1]:null;final int[] sequence={0};
        final PhoneWallpaperServer server=new PhoneWallpaperServer(InetAddress.getByName("127.0.0.1"),1,assets,new PhoneWallpaperServer.Receiver(){public void receive(int scene,InputStream input,PhoneWallpaperServer.Cancellation cancel)throws IOException{byte[] buffer=new byte[8192];int count=0,n;ByteArrayOutputStream data=new ByteArrayOutputStream();while((n=input.read(buffer))!=-1){count+=n;if(destination!=null)data.write(buffer,0,n);}if(destination!=null)Files.write(Paths.get(destination,"upload-"+(++sequence[0])+".jpg"),data.toByteArray());if(count<10)throw new IOException("图片无法解码，请换一张图片");}},null);server.start();
        System.out.println(server.qrAddress());System.out.println(server.pairCode());System.out.flush();
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null){if(line.equals("apply")){if(server.beginApply())server.applied(true);}else if(line.equals("reject"))server.reject();else if(line.equals("quit"))break;}
        server.close();server.awaitClosed();
    }
}
