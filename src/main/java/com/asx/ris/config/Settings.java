package com.asx.ris.config;

public class Settings
{
    public static class DefaultSettings extends Settings
    {
        public DefaultSettings()
        {
            super();
            this.setWebServerPort(7750);
            this.setPortRescanInterval(15000);
            this.setInputBufferSize(2000);
            this.setWebServerRequestSizeMax(256);
            this.setTimeout(10000);
            this.setCommanqQueueDelay(750);
        }
    }

    private int webServerPort;
    private int webServerRequestSizeMax;
    private int portRescanInterval;
    private int inputBufferSize;
    private int timeout;
    private int commanqQueueDelay;
    
    public int getCommanqQueueDelay()
    {
        return commanqQueueDelay;
    }
    
    public void setCommanqQueueDelay(int commanqQueueDelay)
    {
        this.commanqQueueDelay = commanqQueueDelay;
    }

    public int getWebServerPort()
    {
        return webServerPort;
    }

    public void setWebServerPort(int webServerPort)
    {
        this.webServerPort = webServerPort;
    }

    public long getPortRescanInterval()
    {
        return this.portRescanInterval;
    }

    public void setPortRescanInterval(int portRescanInterval)
    {
        this.portRescanInterval = portRescanInterval;
    }

    public int getInputBufferSize()
    {
        return inputBufferSize;
    }

    public void setInputBufferSize(int inputBufferSize)
    {
        this.inputBufferSize = inputBufferSize;
    }

    public int getWebServerRequestSizeMax()
    {
        return webServerRequestSizeMax;
    }

    public void setWebServerRequestSizeMax(int webServerRequestSizeMax)
    {
        this.webServerRequestSizeMax = webServerRequestSizeMax;
    }

    public int getTimeout()
    {
        return timeout;
    }

    public void setTimeout(int timeout)
    {
        this.timeout = timeout;
    }
}
