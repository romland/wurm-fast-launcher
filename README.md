## Faster launching of WU
This mod fixes a nine year old bug that (may) occur on Windows systems with multiple displays at varying resolutions and refresh rates. It manifests itself by more or less freezing your OS for 5-20 seconds when you start up Wurm Unlimited.  

In addition to the bugfix it also adds:  
...one-click login to a server (circumventing launcher) (great for alts!)  
...remove "do you want to quit?" warning  
...show IP in server browser (for when you want to create one-click logins)  

From a development perspective, this makes it reasonable to test mods! :-)  

### How to Install  
Download here: https://github.com/romland/wurm-fast-launcher/releases/tag/1.0

NOTE: this is a _client_ mod. Do not install on server.  
Extract to your client folder (\WurmLauncher) (basically, install like any other mod).  

### How to use  
In addition to the normal mod, there is a .bat file; this one is used to directly log into servers. Just modify character name/server ip/port to what you want and then double click the .bat file to run Wurm Unlimited. You can of course modify this file to start up five alts or whatever too. Note that it must be in the same folder as WurmUmlimited.exe.  

If you don't need the quick-start, well, it still does all the rest by just launching WurmUnlimited.exe like you always did.  

### Caveats on bugfix
- Only one resolution will be shown in Settings -- use custom size instead.  
- Likewise with framerate, if you want another framerate, you can probably edit the settings in e.g. WurmLauncher\PlayerFiles\configs\default (note that you can have many settings files). The field name is called 'fps_limit'.  

### Warnings
- This may or may not work with the beta version of Wurm Unlimited. I have really only tested with latest stable (default) Steam release.  

