package com.asx.ris;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;

import com.asx.ris.web.WebServer;
import com.google.gson.Gson;

public class Util
{
    public static ArrayList<RemoteDevice> BLUETOOTH_DEVICES = new ArrayList<RemoteDevice>();
    
    public static String arrayToJson(String[] data)
    {
        return new Gson().toJson(data);
    }

    public static String toJson(Object o)
    {
        return new Gson().toJson(o);
    }

    public static String getFormattedOutputFromProcess(Process p) throws IOException
    {
        InputStreamReader is = new InputStreamReader(p.getInputStream());
        BufferedReader reader = new BufferedReader(is);
        TypePerfOutputMapping mapping = new TypePerfOutputMapping(reader);
        String json = mapping.toJson();

        if (WebServer.isVerbose())
        {
            System.out.println(json);
        }

        reader.close();

        return json;
    }
    
    public static String readFile(String path)
    {
        StringBuilder builder = new StringBuilder();
 
        try (Stream<String> stream = Files.lines(Paths.get(path), StandardCharsets.UTF_8))
        {
            stream.forEach(s -> builder.append(s).append("\n"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
 
        return builder.toString();
    }

    public static class ComPortEntry
    {
        private String friendlyName;
        private String port;

        public ComPortEntry(String friendlyName, String port)
        {
            this.friendlyName = friendlyName;
            this.port = port;
        }

        public String getFriendlyName()
        {
            return friendlyName;
        }

        public String getPort()
        {
            return port;
        }
        
        @Override
        public String toString()
        {
            return String.format("[%s] %s", getPort(), getFriendlyName());
        }
    }
    
    public static RemoteDevice getRemoteBTDeviceFor(String address)
    {
        RemoteDevice device = null;
        
        for (RemoteDevice btd : BLUETOOTH_DEVICES)
        {
            if (btd.getBluetoothAddress().equalsIgnoreCase(address))
            {
                System.out.println("Bluetooth device match found.");
                return btd;
            }
        }
        
        return device;
    }
    
    public static void updateListOfBluetoothDevices()
    {
        try
        {
            LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, new DiscoveryListener() {

                public ArrayList<RemoteDevice> btDevices = new ArrayList<RemoteDevice>();
                
                @Override
                public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod)
                {
                    btDevices.add(btDevice);
                }

                @Override
                public void inquiryCompleted(int discType)
                {
                    Util.BLUETOOTH_DEVICES.clear();
                    
                    for (RemoteDevice device : btDevices)
                    {
                        Util.BLUETOOTH_DEVICES.add(device);
                    }
                }

                @Override
                public void serviceSearchCompleted(int transID, int respCode)
                {
                    ;
                }

                @Override
                public void servicesDiscovered(int transID, ServiceRecord[] servRecord)
                {
                    ;
                }
            });
        }
        catch (BluetoothStateException e)
        {
            e.printStackTrace();
        }
    }

    public static ArrayList<ComPortEntry> getListOfComPorts()
    {
        String hive = "HKLM";
        String key = "HARDWARE\\DEVICEMAP\\SERIALCOMM";
        ArrayList<ComPortEntry> comPorts = new ArrayList<ComPortEntry>();
        Process p;

        try
        {
            p = Runtime.getRuntime().exec("reg query " + hive + "\\" + key);
            String result = readProcessOutput(p);
            String[] lines = result.split("\n");

            for (String s : lines)
            {
                if (!s.isEmpty() && !s.contains(key))
                {
                    String[] values = s.split("    ");
                    comPorts.add(new ComPortEntry(values[1], values[3]));
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return comPorts;
    }

    public static String readProcessOutput(Process p) throws Exception
    {
        p.waitFor();
        InputStream stream = p.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        String buffer = "";

        while (reader.ready())
        {
            String line = reader.readLine();
            buffer = buffer + line + "\n";
        }

        return buffer;
    }
}
