/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.launcher;

import java.awt.*;
import java.io.*;

import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.launchutils.*;

import org.apache.felix.main.*;

/**
 * Starts the SIP Communicator.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Sebastien Vincent
 */
public class SIPCommunicator
{
    /**
     * The name of the property that stores our home dir location.
     */
    public static final String PNAME_SC_HOME_DIR_LOCATION =
            "net.java.sip.communicator.SC_HOME_DIR_LOCATION";

    /**
     * The name of the property that stores our home dir name.
     */
    public static final String PNAME_SC_HOME_DIR_NAME =
            "net.java.sip.communicator.SC_HOME_DIR_NAME";

    /**
     * The currently active name.
     */
    private static String overridableDirName = "Jitsi";

    /**
     * Legacy home directory names that we can use if current dir name
     * is the crrently active name (overridableDirName).
     */
    private static String[] legacyDirNames =
        {".sip-communicator", "SIP Communicator"};

    /**
     * Starts the SIP Communicator.
     *
     * @param args command line args if any
     *
     * @throws Exception whenever it makes sense.
     */
    public static void main(String[] args)
        throws Exception
    {
        // this needs to be set before any DNS lookup is run (which includes the
        // Java SecurityManager)
        System.setProperty("sun.net.spi.nameservice.provider.1", "dns,dnsjava");

        String version = System.getProperty("java.version");
        String vmVendor = System.getProperty("java.vendor");
        String osName = System.getProperty("os.name");

        setSystemProperties(osName);

        /*
         * SC_HOME_DIR_* are specific to the OS so make sure they're configured
         * accordingly before any other application-specific logic depending on
         * them starts (e.g. Felix).
         */
        setScHomeDir(osName);

        if (version.startsWith("1.4") || vmVendor.startsWith("Gnu") ||
                vmVendor.startsWith("Free"))
        {
            String os = "";

            if (osName.startsWith("Mac"))
                os = ChangeJVMFrame.MAC_OSX;
            else if (osName.startsWith("Linux"))
                os = ChangeJVMFrame.LINUX;
            else if (osName.startsWith("Windows"))
                os = ChangeJVMFrame.WINDOWS;

            ChangeJVMFrame changeJVMFrame = new ChangeJVMFrame(os);
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

            changeJVMFrame.setLocation(
                screenSize.width/2 - changeJVMFrame.getWidth()/2,
                screenSize.height/2 - changeJVMFrame.getHeight()/2);
            changeJVMFrame.setVisible(true);

            return;
        }

        //first - pass the arguments to our arg handler
        LaunchArgHandler argHandler = LaunchArgHandler.getInstance();
        int argHandlerRes = argHandler.handleArgs(args);

        if ( argHandlerRes == LaunchArgHandler.ACTION_EXIT
             || argHandlerRes == LaunchArgHandler.ACTION_ERROR)
        {
            System.exit(argHandler.getErrorCode());
        }

        //lock our config dir so that we would only have a single instance of
        //sip communicator, no matter how many times we start it (use mainly
        //for handling sip: uris after starting the application)
        if ( argHandlerRes != LaunchArgHandler.ACTION_CONTINUE_LOCK_DISABLED )
        {
            switch (new SipCommunicatorLock().tryLock(args))
            {
            case SipCommunicatorLock.LOCK_ERROR:
                System.err.println("Failed to lock SIP Communicator's "
                                +"configuration directory.\n"
                                +"Try launching with the --multiple param.");
                System.exit(SipCommunicatorLock.LOCK_ERROR);
                break;
            case SipCommunicatorLock.ALREADY_STARTED:
                System.out.println(
                    "SIP Communicator is already running and will "
                    +"handle your parameters (if any).\n"
                    +"Launch with the --multiple param to override this "
                    +"behaviour.");

                //we exit with success because for the user that's what it is.
                System.exit(SipCommunicatorLock.SUCCESS);
                break;
            case SipCommunicatorLock.SUCCESS:
                //Successfully locked, continue as normal.
                break;
            }
        }

        //there was no error, continue;
        System.setOut(new ScStdOut(System.out));
        Main.main(new String[0]);
    }

    /**
     * Sets the system properties net.java.sip.communicator.SC_HOME_DIR_LOCATION
     * and net.java.sip.communicator.SC_HOME_DIR_NAME (if they aren't already
     * set) in accord with the OS conventions specified by the name of the OS.
     *
     * @param osName the name of the OS according to which the SC_HOME_DIR_*
     *            properties are to be set
     */
    private static void setScHomeDir(String osName)
    {
        /*
         * Though we'll be setting the SC_HOME_DIR_* property values depending
         * on the OS running the application, we have to make sure we are
         * compatible with earlier releases i.e. use
         * ${user.home}/.sip-communicator if it exists (and the new path isn't
         * already in use).
         */
        String location = System.getProperty(PNAME_SC_HOME_DIR_LOCATION);
        String name = System.getProperty(PNAME_SC_HOME_DIR_NAME);

        boolean isHomeDirnameForced = name != null;

        if ((location == null) || (name == null))
        {
            String defaultLocation = System.getProperty("user.home");
            String defaultName = ".jitsi";

            // Whether we should check legacy names
            // 1) when such name is not forced we check
            // 2) if such is forced and is the overridableDirName check it
            //      (the later is the case with name transition SIP Communicator
            //      -> Jitsi, check them only for Jitsi)
            boolean chekLegacyDirNames = (name == null) ||
                name.equals(overridableDirName);

            if (osName.startsWith("Mac"))
            {
                if (location == null)
                    location =
                            System.getProperty("user.home") + File.separator
                            + "Library" + File.separator
                            + "Application Support";
                if (name == null)
                    name = "Jitsi";
            }
            else if (osName.startsWith("Windows"))
            {
                /*
                 * Primarily important on Vista because Windows Explorer opens
                 * in %USERPROFILE% so .sip-communicator is always visible. But
                 * it may be a good idea to follow the OS recommendations and
                 * use APPDATA on pre-Vista systems as well.
                 */
                if (location == null)
                    location = System.getenv("APPDATA");
                if (name == null)
                    name = "Jitsi";
            }

            /* If there're no OS specifics, use the defaults. */
            if (location == null)
                location = defaultLocation;
            if (name == null)
                name = defaultName;

            /*
             * As it was noted earlier, make sure we're compatible with previous
             * releases. If the home dir name is forced (set as system property)
             * doesn't look for the default dir.
             */
            if (!isHomeDirnameForced
                && (new File(location, name).isDirectory() == false)
                && new File(defaultLocation, defaultName).isDirectory())
            {
                location = defaultLocation;
                name = defaultName;
            }

            // if we need to check legacy names and there is no
            // current home dir already created
            if(chekLegacyDirNames
               && !new File(location, name).isDirectory())
            {
                // now check whether some of the legacy dir names
                // exists, and use it if exist
                for(int i = 0; i < legacyDirNames.length; i++)
                {
                    // check the platform specific directory
                    if(new File(location, legacyDirNames[i]).isDirectory())
                    {
                        name = legacyDirNames[i];
                        break;
                    }

                    // now check it and in the default location
                    if(new File(defaultLocation, legacyDirNames[i]).isDirectory())
                    {
                        name = legacyDirNames[i];
                        location = defaultLocation;
                        break;
                    }
                }
            }

            System.setProperty(PNAME_SC_HOME_DIR_LOCATION, location);
            System.setProperty(PNAME_SC_HOME_DIR_NAME, name);
        }

        // when we end up with the home dirs, make sure we have log dir
        new File(location, name + File.separator + "log").mkdirs();
    }

    /**
     * Sets some system properties specific to the OS that needs to be set at
     * the very beginning of a program (typically for UI related properties,
     * before AWT is launched).
     *
     * @param osName OS name
     */
    private static void setSystemProperties(String osName)
    {
        // setup here all system properties that need to be initialized at
        // the very beginning of an application
        if(osName.startsWith("Windows"))
        {
            // disable Direct 3D pipeline (used for fullscreen) before
            // displaying anything (frame, ...)
            System.setProperty("sun.java2d.d3d", "false");
        }
        else if(osName.startsWith("Mac"))
        {
            // On Mac OS X when switch in fullscreen, all the monitors goes
            // fullscreen (turns black) and only one monitors has images
            // displayed. So disable this behavior because somebody may want
            // to use one monitor to do other stuff while having other ones with
            // fullscreen stuff.
            System.setProperty("apple.awt.fullscreencapturealldisplays",
                "false");
        }
    }
}
