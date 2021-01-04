package com.asx.ris;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import javax.bluetooth.RemoteDevice;

import com.asx.ris.config.Config;
import com.asx.ris.module.IgnitionModule;
import com.asx.ris.web.WebServer;

public class ServiceWrapper
{
    private static final File            CONFIG_FILE   = new File("settings.json");

    private static IgnitionModule        module;
    private static boolean               appRunning    = false;
    private static long                  reconnectTime = 0;
    private static Config                config;
    private static String                error;

    private static PrintStream           originalOutputBuffer;
    private static PrintStream           redirectOutputBuffer;
    private static ByteArrayOutputStream redirectOutputStream;

    public static void main(String[] args)
    {
        // redirectConsole();
        System.out.println("Service starting...");
        config = new Config(CONFIG_FILE);
        config.load();
        WebServer.startWebServer();
        appRunning = true;

        while (appRunning)
        {
            if (module == null)
            {
                long time = System.currentTimeMillis();

                if (reconnectTime > 0 && time - reconnectTime > ServiceWrapper.config.settings().getPortRescanInterval() || reconnectTime == 0)
                {
                    reconnectTime = time;
                    System.out.println(String.format("Refreshing Bluetooth device list (%sms)...", ServiceWrapper.config.settings().getPortRescanInterval()));
                    Util.updateListOfBluetoothDevices();
                }
            }

            if (module != null)
            {
                module.update();
            }

            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                ServiceWrapper.setError("sleep_thread_error");
            }
        }
    }

    public static boolean connectToBluetoothDevice(String address)
    {
        if (getIgnitionModule() != null)
        {
            System.out.println("Terminating existing bluetooth connection.");
            module.close();
            module = null;
        }

        RemoteDevice device = Util.getRemoteBTDeviceFor(address);

        if (device == null)
        {
            return false;
        }

        module = new IgnitionModule();
        module.setBluetoothDevice(device);
        module.setCanConnect(true);

        return true;
    }

    public static void redirectConsole()
    {
        redirectOutputStream = new ByteArrayOutputStream();
        redirectOutputBuffer = new PrintStream(redirectOutputStream);
        originalOutputBuffer = System.out;

        System.setOut(redirectOutputBuffer);
    }

    public static String getConsoleOutput()
    {
        return redirectOutputStream.toString();
    }

    public static void disableConsoleRedirect()
    {
        System.out.flush();
        System.setOut(originalOutputBuffer);
    }

    public static IgnitionModule getIgnitionModule()
    {
        return module;
    }

    public static void killModule()
    {
        if (module != null && module.isConnected())
        {
            module.close();
        }

        module = null;
    }

    public static void terminate()
    {
        appRunning = false;
    }

    public static boolean isAppRunning()
    {
        return appRunning;
    }

    public static void disconnectModule()
    {
        module = null;
    }

    public static Config config()
    {
        return config;
    }

    public static String getError()
    {
        return error;
    }

    public static void setError(String error)
    {
        ServiceWrapper.error = error;
    }
}
