package com.asx.ris.module;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

import com.asx.ris.ServiceWrapper;

public class IgnitionModule
{
    private InputStream        input;
    private OutputStream       output;
    private long               conStartTimeStamp;
    private long               timeSinceLastRead;
    private boolean            isConnected;
    private volatile boolean   canConnect;
    private String             lineBuffer;
    private ArrayList<String>  buffer;
    private boolean            deviceReady;
    private int                timeout;
    private RemoteDevice       bluetoothDevice;
    private StreamConnection   bluetoothConnection;
    private BTConnectionStatus bluetoothStatus;
    private Thread             monitorThread;

    private long               timeSinceLastCommand;

    public static enum BTConnectionStatus
    {
        DISCONNECTED("disconnected"), CONNECTING("connecting"), CONNECTED("connected"), UNAVAILABLE("device_unavailable");

        private String id;

        BTConnectionStatus(String id)
        {
            this.id = id;
        }

        public String getId()
        {
            return id;
        }
    }

    public IgnitionModule()
    {
        this.buffer = new ArrayList<String>();
        this.bluetoothStatus = BTConnectionStatus.DISCONNECTED;
        this.commandQueue = new ArrayList<QueuedCommand>();
        System.out.println("Instantiating ignition module profile");
    }

    public void update()
    {
        long time = System.currentTimeMillis();
        long timeSinceLastRead = this.getTimeSinceLastRead();

        if (isConnected && timeSinceLastRead > 0 && time - timeSinceLastRead > timeout)
        {
            System.out.println("Connection to module timed out...");
            this.close();
            ServiceWrapper.setError("connect_timeout");
            setBluetoothStatus(BTConnectionStatus.UNAVAILABLE);
            this.connectionFailed();
        }

        if (isConnected())
        {
            if (timeSinceLastCommand == 0 || System.currentTimeMillis() - timeSinceLastCommand >= ServiceWrapper.config().settings().getCommanqQueueDelay())
            {
                if (this.commandQueue.size() >= 1)
                {
                    if (this.commandQueue.iterator().hasNext())
                    {
                        QueuedCommand nextCommand = this.commandQueue.iterator().next();
                        System.out.println("Sending queued command: " + nextCommand.getCommand());
                        send(nextCommand.getCommand());
                        this.commandQueue.remove(nextCommand);
                        timeSinceLastCommand = System.currentTimeMillis();
                    }
                }
            }
        }

        if (this.canConnect())
        {
            try
            {
                this.connect();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            this.setCanConnect(false);
        }
    }

    public String readParameter(String parameter, String line)
    {
        return readParameter(parameter, null, line);
    }

    public String readParameter(String parameter, String nextParameter, String line)
    {
        if (line.startsWith(parameter))
        {
            int idxStart = line.lastIndexOf(parameter);
            int idxEnd = line.length();

            String val = line.substring(idxStart, idxEnd);
            val = val.replace(parameter + " ", "");
            val = val.trim();

            if (nextParameter != null && !nextParameter.isEmpty())
            {
                send(nextParameter); // Request the next parameter
            }

            return val;
        }

        return null;
    }

    public void parseDeviceParameters(String line)
    {
        if (line.contains("Init"))
        {
            System.out.println("Ignition module ackowledge packet received.");
            deviceReady = true;
        }
    }

    public void onLineRead(String line)
    {
        timeSinceLastRead = System.currentTimeMillis();

        if (!deviceReady)
        {
            this.parseDeviceParameters(line);
        }
        else
        {
            this.parseSerialData(line);
        }
    }

    private void parseSerialData(String line)
    {
        ;
    }

    public void connectionFailed()
    {
        this.isConnected = false;
        ServiceWrapper.killModule();
    }

    private boolean serviceScanFinished = false;
    private String  accessUrl;

    public void connect() throws IOException, InterruptedException
    {
        this.connect(0);
    }

    public void connect(int count) throws IOException, InterruptedException
    {
        this.setBluetoothStatus(BTConnectionStatus.CONNECTING);
        this.timeout = ServiceWrapper.config().settings().getTimeout();

        UUID uuid = new UUID(0x1101);
        UUID[] searchUuidSet = new UUID[] { uuid };
        int[] attrIDs = new int[] {
                0x0100
        };

        LocalDevice.getLocalDevice().getDiscoveryAgent().searchServices(attrIDs, searchUuidSet,
                getBluetoothDevice(), new DiscoveryListener() {
                    @Override
                    public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod)
                    {
                    }

                    @Override
                    public void inquiryCompleted(int discType)
                    {
                    }

                    @Override
                    public void serviceSearchCompleted(int transID, int respCode)
                    {
                        serviceScanFinished = true;
                    }

                    @Override
                    public void servicesDiscovered(int transID, ServiceRecord[] servRecord)
                    {
                        for (int i = 0; i < servRecord.length; i++)
                        {
                            accessUrl = servRecord[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                            if (accessUrl != null)
                            {
                                break;
                            }
                        }
                    }
                });

        while (!serviceScanFinished)
        {
            Thread.sleep(500);
        }

        if (accessUrl != null && !accessUrl.isEmpty())
        {
            System.out.println("Initiating connection to device: " + accessUrl);

            bluetoothConnection = (StreamConnection) Connector.open(accessUrl);
            output = bluetoothConnection.openOutputStream();
            input = bluetoothConnection.openInputStream();
            this.connectionEstablished();
        }
        else
        {
            System.out.println("Bluetooth device unavailable or out of range.");
            this.setBluetoothStatus(BTConnectionStatus.UNAVAILABLE);

            if (count <= 3)
            {
                Thread.sleep(5000);
                System.out.println("Re-attempting connection (" + count + ")...");
                connect(count + 1);
            }

            if (count >= 3)
            {
                System.out.println("Connection to device failed. Too many connection attempts.");
                this.close();
                ServiceWrapper.disconnectModule();
            }
        }
    }

    private void connectionEstablished()
    {
        System.out.println("Connected.");
        this.isConnected = true;
        this.setBluetoothStatus(BTConnectionStatus.CONNECTED);
        // monitorThread = new Thread(new BluetoothMonitorThread(this));
        // monitorThread.start();
    }

    public boolean reset()
    {
        return send("reset");
    }

    public void heartbeat()
    {
        this.timeSinceLastRead = System.currentTimeMillis();
    }

    // @Override
    // public synchronized void serialEvent(SerialPortEvent oEvent)
    // {
    // if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE)
    // {
    // try
    // {
    // while (input.available() > 0)// input.ready()
    // {
    // char c = (char) input.read();
    // this.lineBuffer = this.lineBuffer + c;
    // // System.out.print(c);
    //
    // if (c == '\n')
    // {
    // String line = this.lineBuffer.trim();
    //
    // this.onLineRead(line);
    // this.buffer.add(line);
    // this.lineBuffer = "";
    // }
    // }
    //
    // if (this.buffer.size() >
    // ServiceWrapper.config().settings().getInputBufferSize())
    // {
    // this.buffer.clear();
    // }
    // }
    // catch (Exception e)
    // {
    // e.printStackTrace();
    // ServiceWrapper.setError("serial_event: " + e.toString());
    // }
    // }
    // }

    public synchronized void close()
    {
        if (bluetoothConnection != null)
        {
            try
            {
                bluetoothConnection.close();
                input.close();
                output.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        this.timeSinceLastRead = 0;
        this.isConnected = false;
        this.bluetoothConnection = null;
        this.deviceReady = false;
        ServiceWrapper.disconnectModule();
        this.setBluetoothStatus(BTConnectionStatus.DISCONNECTED);
    }

    public void receive(byte[] data, int received)
    {
        System.out.println(new String(data, StandardCharsets.UTF_8));

        // while (input.available() > 0)// input.ready()
        // {
        // char c = ;
        // this.lineBuffer = this.lineBuffer + c;
        // // System.out.print(c);
        //
        // if (c == '\n')
        // {
        // String line = this.lineBuffer.trim();
        //
        // this.onLineRead(line);
        // this.buffer.add(line);
        // this.lineBuffer = "";
        // }
        // }

        if (this.buffer.size() > ServiceWrapper.config().settings().getInputBufferSize())
        {
            this.buffer.clear();
        }
    }

    public boolean send(String data)
    {
        try
        {
            if (output == null)
            {
                return false;
            }

            byte[] bytes = data.getBytes();
            output.write(bytes);
            return true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            this.close();
            return false;
        }
    }

    public long getTimeSinceLastRead()
    {
        return timeSinceLastRead;
    }

    public long getConStartTimeStamp()
    {
        return conStartTimeStamp;
    }

    public void setConStartTimeStamp(long conStartTimeStamp)
    {
        this.conStartTimeStamp = conStartTimeStamp;
    }

    public boolean isConnected()
    {
        return isConnected;
    }

    public ArrayList<String> getBuffer()
    {
        return buffer;
    }

    public boolean canConnect()
    {
        return canConnect;
    }

    public synchronized void setCanConnect(boolean v)
    {
        this.canConnect = v;
    }

    public boolean isDeviceReady()
    {
        return deviceReady;
    }

    public RemoteDevice getBluetoothDevice()
    {
        return bluetoothDevice;
    }

    public StreamConnection getBluetoothConnection()
    {
        return bluetoothConnection;
    }

    public Thread getBluetoothMonitorThread()
    {
        return monitorThread;
    }

    public void setBluetoothDevice(RemoteDevice bluetoothDevice)
    {
        this.bluetoothDevice = bluetoothDevice;
    }

    public BTConnectionStatus getBluetoothStatus()
    {
        return bluetoothStatus;
    }

    public void setBluetoothStatus(BTConnectionStatus bluetoothStatus)
    {
        this.bluetoothStatus = bluetoothStatus;
    }
    
    public static class QueuedCommand
    {
        private int id;
        private String command;
        
        public QueuedCommand(int id, String command)
        {
            this.id = id;
            this.command = command;
        }
        
        public int getId()
        {
            return id;
        }
        
        public String getCommand()
        {
            return command;
        }
    }

    private ArrayList<QueuedCommand> commandQueue;

    public void ignite(int channel)
    {
        commandQueue.add(new QueuedCommand(commandQueue.size(), "x " + channel));
    }

    public void setPower(int channel, int power)
    {
        commandQueue.add(new QueuedCommand(commandQueue.size(), "power " + channel + " " + power));
    }

    public void setDuration(int channel, int duration)
    {
        commandQueue.add(new QueuedCommand(commandQueue.size(), "duration " + channel + " " + duration));
    }
}