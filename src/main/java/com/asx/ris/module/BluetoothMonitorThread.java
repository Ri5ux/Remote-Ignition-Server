package com.asx.ris.module;

import java.io.InputStream;

public class BluetoothMonitorThread implements Runnable
{
    private IgnitionModule module;
    private static boolean running = true;

    public BluetoothMonitorThread(IgnitionModule module)
    {
        this.module = module;
    }

    @Override
    public void run()
    {
        try
        {
            InputStream inputStream = module.getBluetoothConnection().openInputStream();

            byte[] input = new byte[64];
            int received;

            while (BluetoothMonitorThread.running)
            {
                received = inputStream.read(input);
                module.receive(input, received);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    public IgnitionModule getModule()
    {
        return module;
    }
    
    public void terminate()
    {
        running = false;
    }
}