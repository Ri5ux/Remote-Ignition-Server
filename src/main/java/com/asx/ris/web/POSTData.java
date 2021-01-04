package com.asx.ris.web;

import java.util.ArrayList;

public class POSTData
{
    public static class DataPair
    {
        public String key;
        public String val;

        public DataPair(String key, String val)
        {
            this.key = key;
            this.val = val;
        }

        public String getKey()
        {
            return key;
        }

        public String getValue()
        {
            return val;
        }
    }

    private ArrayList<DataPair> data;

    public POSTData(ArrayList<String> input)
    {
        this.data = new ArrayList<DataPair>();

        if (!input.isEmpty())
        {
            for (String pair : input)
            {
                String[] a = pair.split("=");

                if (a.length > 1)
                {
                    this.data.add(new DataPair(a[0], a[1]));
                }
            }
        }
    }

    public ArrayList<DataPair> getData()
    {
        return data;
    }

    public String get(String key)
    {
        String value = "";

        for (DataPair pair : this.data)
        {
            if (pair.getKey().toLowerCase().equalsIgnoreCase(key.toLowerCase()))
            {
                value = pair.getValue();
            }
        }

        return value;
    }
}
