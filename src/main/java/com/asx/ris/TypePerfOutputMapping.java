package com.asx.ris;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class TypePerfOutputMapping
{
    private String keys;
    private String values;

    public TypePerfOutputMapping(BufferedReader out)
    {
        try
        {
            String line = null;
            int lineNumber = 0;

            while ((line = out.readLine()) != null)
            {
                if (lineNumber == 1)
                {
                    this.keys = line;
//                    System.out.println("Keys: " + this.keys);
                }
                if (lineNumber == 2)
                {
                    this.values = line;
//                    System.out.println("Values: " + this.values);
                }
                lineNumber++;
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.keys = "";
            this.values = "";
        }
    }
    
    private static String stripVersionInfoFromKeys(String k)
    {
//        String versionMarker = "\"(PDH-CSV";
//        String versionMarkerEnd = ")\",";
//        
//        if (k.contains(versionMarker))
//        {
//            String versionString = k.substring(k.indexOf(versionMarker), k.indexOf(versionMarkerEnd) + versionMarkerEnd.length());
//            k = k.replace(versionString, "");
//        }
        
        return k;
    }
    
    private static String stripQuotes(String k)
    {
        return k.replace("\"", "");
    }
    
    public Map<String, String> toMap()
    {
        HashMap<String, String> map = new HashMap<String, String>();
        
        String[] ks = this.keys.split("\",");
        String[] vs = this.values.split("\",");
        
        for (int i = ks.length - 1; i > 0; i--)
        {
            String k = stripQuotes(ks[i]);
            String v = stripQuotes(vs[i]);
            
            map.put(k, v);
        }
        
        return map;
    }
    
    public String toJson()
    {
        return new Gson().toJson(toMap());
    }
    
    public String getValues()
    {
        return values;
    }
    
    public String getKeys()
    {
        return keys;
    }
}
