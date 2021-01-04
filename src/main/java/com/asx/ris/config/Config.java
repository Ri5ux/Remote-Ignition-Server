package com.asx.ris.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.asx.ris.Util;
import com.asx.ris.config.Settings.DefaultSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Config
{
    private static final Settings DEFAULT_SETTINGS = new DefaultSettings();

    private Settings              settings;
    private File                  file;

    public Config(File file)
    {
        this.file = file;
    }

    public void load()
    {
        System.out.println("Loading configuration: " + this.file.getAbsolutePath());

        if (file.exists())
        {
            String configContents = Util.readFile(this.file.getPath());

            if (!configContents.isEmpty())
            {
                Gson gson = new Gson();
                this.settings = gson.fromJson(configContents, Settings.class);
                this.validateAgainst(this.settings, DEFAULT_SETTINGS);
                System.out.println("Configuration loaded.");
            }
            else
            {
                System.out.println("Configuration empty. Setting defaults and saving new configuration file...");
                this.saveDefault();
                this.load();
            }
        }
        else
        {
            System.out.println("Configuration does not exist. Creating a new config and saving the default settings...");
            this.saveDefault();
            this.load();
        }
    }
    
    public void validateAgainst(Settings active, Settings defaults)
    {
        //TODO: Implement config validation
    }

    public void saveDefault()
    {
        if (!this.file.exists())
        {
            try
            {
                this.file.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        System.out.println("Attemping to save new default configuration...");
        this.save(DEFAULT_SETTINGS);
    }

    public boolean save()
    {
        return this.save(this.settings);
    }

    public boolean save(Settings settings)
    {
        if (file.exists())
        {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            try (FileWriter writer = new FileWriter(this.file.getPath()))
            {
                gson.toJson(this.settings = DEFAULT_SETTINGS, writer);
                System.out.println("Configuration saved.");
                return true;
            }
            catch (IOException e)
            {
                e.printStackTrace();
                System.out.println("Failed to save configuration to file.");
            }
        }
        else
        {
            System.out.println("Failed to create new config file.");
        }

        return false;
    }
    
    public String settingsAsJson()
    {
        if (this.settings != null)
        {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(this.settings);
        }
        
        return null;
    }

    public File getFile()
    {
        return file;
    }

    public Settings settings()
    {
        return settings;
    }
}
