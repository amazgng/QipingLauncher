package cn.wengmang.qiping;

import android.content.Context;
import android.net.*;
import java.net.*;

/** Uses Wi-Fi/Ethernet link addresses without reading the SSID or asking for location access. */
final class LanAddress {
    static InetAddress find(Context context){
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager==null)return null;
        try{
            Network active=manager.getActiveNetwork();
            InetAddress chosen=address(manager,active);if(chosen!=null)return chosen;
            for(Network network:manager.getAllNetworks()){chosen=address(manager,network);if(chosen!=null)return chosen;}
        }catch(RuntimeException ignored){}
        return null;
    }
    private static InetAddress address(ConnectivityManager manager,Network network){
        if(network==null)return null;NetworkCapabilities caps=manager.getNetworkCapabilities(network);
        if(caps==null||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)||!(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))return null;
        LinkProperties link=manager.getLinkProperties(network);if(link==null)return null;
        for(LinkAddress entry:link.getLinkAddresses()){InetAddress address=entry.getAddress();if(address instanceof Inet4Address&&address.isSiteLocalAddress())return address;}
        return null;
    }
    private LanAddress(){}
}
