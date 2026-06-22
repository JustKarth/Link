Each device will have some amount of persistent memory which will be saved in a folder which contains stuff like its unique IDs, trusted devices etc.

Each device must have some uniquely identifiable identity that is very improbable to coincide. UUID will be used here. Version tbd
Each device will have a display name.
Each device can store its trusted devices with some other nickname which will be stored only on local
Each device stores the current device it is connected to.

There will be authentication by verifying the end devices using asymetric encryption keys.

Fresh Connection Process:
    Let us say we have device 1 and device 2.
    Both of them must be in the staged position using /stage (Both of them will be discoverable on the LAN)
    Now we run /viewDevices on device 1 and we can see all devices on the LAN that are staged. We can see device 2 with a number prefixed to it
    Now we run /connect prefix, and this will send a connection request to device 2.
    We can enter Y/n in device 2. If we type n, both devices are in staging mode again
    If we type Y, device 2 will generate a temporary auth key for validating device 1 (6 char code, 30s window)
    Now this code will be typed in device 1 and then device 1 will send it to device 2 to verify.
    After this device 1 generates a code and it has to be entered in device 2 for verification. Now they are mutually verified
    Now the authentication is done.
    There will be a prompt on both devices -> "Trust this device? [Y/n]"
    YY - Symmetric trust exists
    YN or NY - Asymmetric trust exists
    NN - No trust

Trust Connection Process:
    Being a trusted device only skips the authentication part but you still need to stage it manually to autoconnect. 
    This is so that there is no autoconnection when you just started link incase you want to untrust a device.
    
    Let us say we started up device 1.
    We staged device 1.
    Now it will autoconnect with all the devices it has symmetric trust with which are staged.
    Other than this for cases of asymmetric trust there has to be a Fresh Connection Process again but the auth isn't done for the trusted direction. (This will have to be added in fresh connection process as a check too)


Modes:

    Chat mode: You directly type the message

    File Transfer mode: You transfer files using the commands

    Remote Shell mode: Gives you access to remote shell

    Config mode: You can configure your link preferences in this mode

Data:
    Persistent:
        UUIDv4
        Private Key
        Public Key
        Display Name
        Trusted Devices (List)
        Config preferences
    Runtime:
        Current Mode
        IP Address
        Current Device
        Connected Devices
        Auth Code (temporary)
        

The broadcast commands allow us to send something to all the trusted devices at a given time. Right now we have announce and distribute.
Announce sends the message to connected devices with a prefix saying its an announcement from so and so device (whatever the nickname is).
Distribute will send the prompt about sending the file to all devices and will transfer the file if accepted on the other end with Y. Else it won't be downloaded. The sender device gets an acknowledgement of who accepted the files and who rejected them.

The cli can handle one device at a time. To switch to another device's control you must be connected and run the relevant command

NOTE: Only the commands which run irrespective of mode have the prefix '/'