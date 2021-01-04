package com.asx.ris.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import com.asx.ris.Util;

public abstract class RequestHandler
{
    private String request;
    private ArrayList<String> headers;
    private POSTData postData;

    public RequestHandler(String request)
    {
        this.request = request;
    }

    public String getRequest()
    {
        return request;
    }

    public void handleRequest(PrintWriter out, OutputStream dataOut, ArrayList<String> headers, POSTData postData)
    {
        this.headers = headers;
        this.postData = postData;
    }

    public static interface IDataHandler
    {
        public Object getData(RequestHandler handler);
    }
    
    public ArrayList<String> getHeaders()
    {
        return headers;
    }
    
    public POSTData getPOSTData()
    {
        return postData;
    }

    public static class StandardRequestHandler extends RequestHandler
    {
        private IDataHandler dataHandler;

        public StandardRequestHandler(String request, IDataHandler iDataHandler)
        {
            super(request);
            this.dataHandler = iDataHandler;
        }

        @Override
        public void handleRequest(PrintWriter out, OutputStream dataOut, ArrayList<String> headers, POSTData postData)
        {
            super.handleRequest(out, dataOut, headers, postData);
            
            Object data = this.dataHandler.getData(this);
            
            if (data == null)
            {
                data = "";
            }
            
            int dataLength = 0;
            byte[] bytes = null;

            try
            {
                if (data instanceof String)
                {
                    String text = (String) data;
                    dataLength = text.length();
                    bytes = text.getBytes();

                    WebServer.buildGenericHeader(out, dataOut, dataLength);
                }

                WebServer.sendData(out, dataOut, bytes, dataLength);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        public IDataHandler getDataHandler()
        {
            return dataHandler;
        }
    }

    public static class CommandRequestHandler extends RequestHandler
    {
        private String command;

        public CommandRequestHandler(String request, String command)
        {
            super(request);
            this.command = command;
        }

        public void handleRequest(PrintWriter out, OutputStream dataOut, ArrayList<String> headers, POSTData postData)
        {
            super.handleRequest(out, dataOut, headers, postData);
            
            try
            {
                Process p = Runtime.getRuntime().exec(command);
                String o = Util.getFormattedOutputFromProcess(p);

                WebServer.buildGenericHeader(out, dataOut, o.length());
                WebServer.sendData(out, dataOut, o.getBytes(), o.length());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        public String getCommand()
        {
            return command;
        }
    }
}