package com.cloud.network.as;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class LogStashClientImpl implements LogStashClient, Configurable {

    private static final ConfigKey<String> LogStashServer = new ConfigKey<>("Advanced", String.class, "autoscale.logstash.host", "", "Auto scale metrics target logstash host", false, ConfigKey.Scope.Global);
    private static final ConfigKey<Integer> LogStashPort = new ConfigKey<>("Advanced", Integer.class, "autoscale.logstash.port", null, "Auto scale metrics target logstash port", false, ConfigKey.Scope.Global);

    public static Logger s_logger = Logger.getLogger(LogStashClientImpl.class.getName());

    @Override
    public boolean send(String message) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();

            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(LogStashServer.value()), LogStashPort.value());

            socket.send(packet);
            socket.setSoTimeout(500);
        } catch (Exception e) {
            s_logger.error("Error sending data to logstash", e);
            return false;
        } finally {
            if (socket != null)
                socket.close();
        }
        return true;
    }

    @Override
    public String getConfigComponentName() {
        return LogStashClientImpl.class.getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ LogStashServer, LogStashPort};
    }
}
