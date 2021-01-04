package com.asx.ris.web;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.StringTokenizer;

import javax.bluetooth.RemoteDevice;

import com.asx.ris.ServiceWrapper;
import com.asx.ris.Util;
import com.asx.ris.Util.ComPortEntry;
import com.asx.ris.config.Config;
import com.asx.ris.config.Settings;
import com.asx.ris.module.IgnitionModule.BTConnectionStatus;
import com.asx.ris.web.RequestHandler.CommandRequestHandler;
import com.asx.ris.web.RequestHandler.StandardRequestHandler;
import com.google.common.io.ByteStreams;

public class WebServer implements Runnable
{
    public static final File                 WEB_ROOT             = new File(".\\web\\");
    public static final String               DEFAULT_FILE         = "index.html";
    public static final String               METHOD_NOT_SUPPORTED = "not_supported.html";

    private static boolean                   verbose              = false;
    private static boolean                   isRunning            = false;
    private static Thread                    mainThread;

    private Socket                           connection;

    private static ArrayList<RequestHandler> REQUESTS             = new ArrayList<RequestHandler>();

    public WebServer(Socket c)
    {
        connection = c;
        REQUESTS.add(new CommandRequestHandler("/sys/stat/cpu/totalusage", "typeperf -sc 1 \"\\Processor Information(_total)\\% Processor Utility\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/cpu/coreusage", "typeperf -sc 1 \"\\Processor Information(*)\\% Processor Utility\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/cpu/corefrequency", "typeperf -sc 1 \"\\Processor Information(*)\\Processor Frequency\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/memory/total", "typeperf -sc 1 \"\\NUMA Node Memory(*)\\Total MBytes\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/memory/available", "typeperf -sc 1 \"\\NUMA Node Memory(*)\\Available MBytes\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/disk/usage", "typeperf -sc 1 \"\\PhysicalDisk(*)\\% Disk Time\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/disk/readbps", "typeperf -sc 1 \"\\PhysicalDisk(*)\\Disk Read Bytes/sec\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/disk/writebps", "typeperf -sc 1 \"\\PhysicalDisk(*)\\Disk Write Bytes/sec\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/power/milliwatts", "typeperf -sc 1 \"\\Power Meter(*)\\Power\""));
        REQUESTS.add(new CommandRequestHandler("/sys/stat/gpu/usage", "typeperf -sc 1 \"\\GPU Engine(*)\\Utilization Percentage\""));
        REQUESTS.add(new StandardRequestHandler("/module/ignite", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                //System.out.println("Sending ignite command to module...");
                boolean result = false;

                if (ServiceWrapper.getIgnitionModule() != null)
                {
                    String channel = handler.getPOSTData().get("channel");
                    
                    if (channel != null && !channel.isEmpty())
                    {
                        int i = Integer.parseInt(channel);
                        ServiceWrapper.getIgnitionModule().ignite(i);
                        result = true;
                    }
                }

                if (!result)
                {
                    System.out.println("Command failed.");
                }

                return String.valueOf(result);
            }
        }));
        REQUESTS.add(new StandardRequestHandler("/module/apply", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                boolean status = false;

                try
                {
                    int channel = Integer.parseInt(handler.getPOSTData().get("channel"));
                    int duration = Integer.parseInt(handler.getPOSTData().get("duration"));
    
                    if (ServiceWrapper.getIgnitionModule() != null)
                    {
                        
                        //System.out.println("Applying new settings for channel " + channel);
                        ServiceWrapper.getIgnitionModule().setDuration(channel, duration);
                        status = true;
                    }
                    
                } catch(Exception e) {
                    status = false;
                    e.printStackTrace();
                }

                if (!status)
                {
                    System.out.println("Failed to apply settings on channel " + handler.getPOSTData().get("channel"));
                }

                return status;
            }
        }));
        REQUESTS.add(new StandardRequestHandler("/settings", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                return String.valueOf(ServiceWrapper.config().settingsAsJson());
            }
        }));
        REQUESTS.add(new StandardRequestHandler("/error", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                return String.valueOf(ServiceWrapper.getError());
            }
        }));
        REQUESTS.add(new StandardRequestHandler("/console", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                if (ServiceWrapper.getIgnitionModule() != null)
                {
                    String output = ServiceWrapper.getConsoleOutput();
                    String[] lines = output.split("\n");
                    String actualOutput = "";

                    int lineCountMax = 10000;

                    for (int x = lines.length - 1; x >= 0; x--)
                    {
                        String line = lines[x];

                        if (x >= lines.length - lineCountMax)
                        {
                            actualOutput = line + "\n" + actualOutput;
                        }
                    }

                    return String.valueOf(actualOutput);
                }

                return "no_data";
            }
        }));
        REQUESTS.add(new StandardRequestHandler("/sys/bluetooth/devices", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                ArrayList<ArrayList<String>> list = new ArrayList<ArrayList<String>>();

                for (RemoteDevice device : Util.BLUETOOTH_DEVICES)
                {
                    ArrayList<String> deviceInfo = new ArrayList<String>();

                    try
                    {
                        deviceInfo.add(device.getFriendlyName(false));
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                        deviceInfo.add("Generic");
                    }

                    deviceInfo.add(device.getBluetoothAddress());
                    list.add(deviceInfo);
                }

                return Util.toJson(list);
            }
        }));
        REQUESTS.add(new StandardRequestHandler("/sys/bluetooth/devices/select", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                String address = handler.getPOSTData().get("address");
                boolean status = false;
                System.out.println("Trying to connect to bluetooth device with address " + address);

                if (ServiceWrapper.connectToBluetoothDevice(address))
                {
                    status = true;
                }
                else
                {
                    System.out.println("Unable to find bluetooth device with address " + address);
                }

                return status;
            }
        }));
        REQUESTS.add(new StandardRequestHandler("/sys/bluetooth/devices/current", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                if (ServiceWrapper.getIgnitionModule() != null)
                {
                    try
                    {
                        return ServiceWrapper.getIgnitionModule().getBluetoothDevice().getFriendlyName(false);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }

                return "null";
            }
        }));
        REQUESTS.add(new StandardRequestHandler("/sys/bluetooth/status", new RequestHandler.IDataHandler() {
            @Override
            public Object getData(RequestHandler handler)
            {
                if (ServiceWrapper.getIgnitionModule() != null)
                {
                    return ServiceWrapper.getIgnitionModule().getBluetoothStatus().getId();
                }

                return BTConnectionStatus.DISCONNECTED.getId();
            }
        }));
    }

    public static ArrayList<RequestHandler> REQUESTS()
    {
        return REQUESTS;
    }

    public static void startWebServer()
    {
        isRunning = true;
        mainThread = new Thread() {
            public void run()
            {
                ServerSocket socket = null;

                try
                {
                    Config config = ServiceWrapper.config();
                    Settings settings = config.settings();
                    int port = settings.getWebServerPort();
                    socket = new ServerSocket(port);
                    System.out.println("Remote Ignition Server service started.\nListening for connections on port " + port + "...\n");

                    while (isRunning)
                    {
                        WebServer web = new WebServer(socket.accept());
                        Thread thread = new Thread(web);
                        thread.start();
                    }

                }
                catch (IOException e)
                {
                    System.err.println("Server Connection error : " + e.getMessage());
                    ServiceWrapper.setError("server_error: " + e.getMessage());
                }
                finally
                {
                    try
                    {
                        if (socket != null)
                        {
                            socket.close();
                        }
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        };
        mainThread.setDaemon(false);
        mainThread.start();
    }

    public static void kill()
    {
        WebServer.isRunning = false;
    }

    public static void main(String[] args)
    {
        startWebServer();
    }

    @Override
    public void run()
    {
        BufferedReader in = null;
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;
        String request = null;

        if (verbose)
        {
            System.out.println("Connection opened from " + connection.getInetAddress() + " (" + new Date() + ")");
        }

        try
        {
            InputStream stream = ByteStreams.limit(connection.getInputStream(), 1024 * ServiceWrapper.config().settings().getWebServerRequestSizeMax());// Limit the input to 256KB of data
            in = new BufferedReader(new InputStreamReader(stream));
            out = new PrintWriter(connection.getOutputStream());
            dataOut = new BufferedOutputStream(connection.getOutputStream());

            String input = in.readLine();
            StringTokenizer parse = new StringTokenizer(input);
            String method = parse.nextToken().toUpperCase();
            request = parse.nextToken().toLowerCase();

            String headerLine = null;
            ArrayList<String> headers = new ArrayList<String>();

            while ((headerLine = in.readLine()).length() != 0)
            {
                headers.add(headerLine);
            }

            StringBuilder payload = new StringBuilder();

            while (in.ready())
            {
                payload.append((char) in.read());
            }

            String rawPOST = payload.toString();

            if (rawPOST != null && !rawPOST.isEmpty())
            {
                System.out.println("POST: " + rawPOST);
            }

            ArrayList<String> pairs = new ArrayList<String>(Arrays.asList(rawPOST.split("&")));
            POSTData postData = new POSTData(pairs);

            for (RequestHandler handler : new ArrayList<RequestHandler>(REQUESTS))
            {
                if (request.equalsIgnoreCase(handler.getRequest()))
                {
                    handler.handleRequest(out, dataOut, headers, postData);
                    return;
                }
            }

            if (!method.equals("GET") && !method.equals("HEAD"))
            {
                handle501(out, dataOut, request, method);
            }
            else
            {
                if (request.endsWith("/"))
                {
                    request += DEFAULT_FILE;
                }

                if (method.equals("GET"))
                {
                    File file = new File(WEB_ROOT, request);
                    int fileLength = (int) file.length();
                    byte[] fileData = readFileData(file,
                            fileLength);

                    buildGenericHeader(out, dataOut, fileLength);
                    sendData(out, dataOut,
                            fileData, fileLength);
                }

                if (verbose)
                {
                    String content = getContentType(request);
                    System.out.println("File " + request + " of type " + content + " returned");
                }

                // String o = "";
                //
                // WebServer.buildGenericHeader(out, dataOut, o.length());
                // WebServer.sendData(out, dataOut, o.getBytes(), o.length());
            }
        }
        catch (FileNotFoundException fnfe)
        {
            try
            {
                handle404(out, dataOut, request);
            }
            catch (IOException ioe)
            {
                System.err.println("Error handling 404: " + ioe.getMessage());
            }
        }
        catch (IOException ioe)
        {
            System.err.println("Server error: " + ioe);
        }
        finally
        {
            try
            {
                in.close();
                out.close();
                dataOut.close();
                connection.close();
            }
            catch (Exception e)
            {
                System.err.println("Error closing stream: " + e.getMessage());
            }

            if (verbose)
            {
                System.out.println("Connection closed.\n");
            }
        }
    }

    private byte[] readFileData(File file, int fileLength) throws IOException
    {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];

        try
        {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        }
        finally
        {
            if (fileIn != null)
            {
                fileIn.close();
            }
        }

        return fileData;
    }

    private String getContentType(String fileRequested)
    {
        if (fileRequested.endsWith(".htm") || fileRequested.endsWith(".html"))
            return "text/html";
        else
            return "text/plain";
    }

    private void handle404(PrintWriter out, OutputStream dataOut, String request) throws IOException
    {
        String text = "404/NOT FOUND";
        int length = text.length();
        byte[] data = text.getBytes();

        out.println("HTTP/1.1 404 File Not Found");
        out.println("Server: MDXLib HTTP Server 1.0");
        out.println("Date: " + new Date());
        out.println("Content-type: " + "text/html");
        out.println("Content-length: " + length);
        out.println("Access-Control-Allow-Origin: *");
        out.println();
        out.flush();

        sendData(out, dataOut, data, length);
        System.out.println("404 " + request + " not found");
    }

    private void handle501(PrintWriter out, BufferedOutputStream dataOut, String request, String method) throws IOException
    {
        if (verbose)
        {
            System.out.println("501/NOT IMPLEMENTED: " + method + " method.");
        }

        String text = "501/NOT_IMPLEMENTED: " + method;
        int fileLength = (int) text.length();
        String contentMimeType = "text/html";
        byte[] fileData = text.getBytes();

        out.println("HTTP/1.1 501 Not Implemented");
        out.println("Server: MDXLib HTTP Server 1.0");
        out.println("Date: " + new Date());
        out.println("Content-type: " + contentMimeType);
        out.println("Content-length: " + fileLength);
        out.println("Access-Control-Allow-Origin: *");
        out.println();
        out.flush();
        sendData(out, dataOut, fileData, fileLength);
    }

    public static void buildGenericHeader(PrintWriter out, OutputStream dataOut, int dataLength) throws IOException
    {
        out.println("HTTP/1.1 200 OK");
        out.println("Server: MDXLib HTTP Server 1.0");
        out.println("Date: " + new Date());
        out.println("Content-type: " + "text/html");
        out.println("Content-length: " + dataLength);
        out.println("Access-Control-Allow-Origin: *");
        out.println();
        out.flush();
    }

    public static void sendData(PrintWriter out, OutputStream dataOut, byte[] dataBytes, int dataLength) throws IOException
    {
        if (dataOut == null)
        {
            return;
        }

        if (dataBytes == null)
        {
            return;
        }

        dataOut.write(dataBytes, 0, dataLength);
        dataOut.flush();
    }

    public static boolean isVerbose()
    {
        return verbose;
    }
}