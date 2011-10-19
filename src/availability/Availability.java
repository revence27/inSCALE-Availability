/*
 *  TODO:
 *  1.  Store messages
 *  2.  Attempt to send pending messages
 */

package availability;

import java.io.*;
//  import java.lang.*;
import java.util.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.*;
//  import javax.microedition.location.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;
import javax.wireless.messaging.*;

class AvailabilityInformation
{
    private String place, latitude, longitude, sigStrength, network;
    private double bigdl, smalldl;
    private boolean sigAvailable;
    private Vector numbers;

    public AvailabilityInformation(String p, Vector n)
    {
        place   = p;
        numbers = n;
    }

    public void setCoords(String lat, String lng)
    {
        latitude  = lat;
        longitude = lng;
    }

    public void setBigDLTime(double t)
    {
        bigdl = t;
    }

    public void setSmallDLTime(double t)
    {
        smalldl = t;
    }

    public void setSignalAvailability(boolean av)
    {
        sigAvailable = av;
    }

    public void setSignalStrength(String st)
    {
        sigStrength = st;
    }

    public void setNetwork(String nw)
    {
        network = nw;
    }

    public String getMessage()
    {
        return ((sigAvailable ? "Y" : "N") + ":" + sigStrength + "| " +
                latitude + ":" + longitude + " " + Double.toString(bigdl) +
                ":" + Double.toString(smalldl) + " [" + place + "]");
    }

    public String getNetwork()
    {
        return network;
    }

    public Vector getNumbers()
    {
        return numbers;
    }
}

class StoreManager
{
    public static String read(String place) throws RecordStoreException
    {
        return StoreManager.read(place, 1);
    }

    public static String read(String place, int pos) throws RecordStoreException
    {
        RecordStore rs = RecordStore.openRecordStore(place, true);
        String ans = new String(rs.getRecord(pos));
        rs.closeRecordStore();
        return ans;
    }

    public static void write(String place, String what) throws RecordStoreException
    {
        StoreManager.write(place, 0, what);
    }

    public static void write(String place, int pos, String what) throws RecordStoreException
    {
        RecordStore rs = RecordStore.openRecordStore(place, true);
        byte [] data   = what.getBytes();
        rs.addRecord(data, pos, data.length);
        rs.closeRecordStore();
    }
}

interface CarriesAvailabilityInformation
{
    public void setAI(AvailabilityInformation ai);
    public void fetchInfo();
}

class SendSMS extends TextBox implements CommandListener, CarriesAvailabilityInformation, Runnable
{
    private MIDlet mama;
    private Displayable prev, initial;
    private Command beg, send;
    private AvailabilityInformation ave;
    private Thread sdr;
    
    public SendSMS(MIDlet m, Displayable d, Displayable fst)
    {
        super("Availability Data", "", 140, TextField.ANY);
        mama    = m;
        prev    = d;
        initial = fst;
        beg     = new Command("Done", Command.BACK, 1);
        send    = new Command("Record", Command.OK, 0);
        addCommand(beg);
        addCommand(send);
        setCommandListener(this);
    }

    private void pushMessage(String num, String mccoy) throws IOException
    {
        MessageConnection msgc  = (MessageConnection) Connector.open("sms://" + num);
        TextMessage tm = (TextMessage) msgc.newMessage(MessageConnection.TEXT_MESSAGE);
        tm.setPayloadText(mccoy);
        msgc.send(tm);
        msgc.close();
    }

    public void run()
    {
        Vector them         = ave.getNumbers();
        Enumeration numbers = them.elements();
        Alert notif = new Alert("Sending messages",
                "Sending to " + Integer.toString(them.size()) + " network" + (them.size() == 1 ? "" : "s") + " ...",
                null,
                AlertType.INFO);
        Display.getDisplay(mama).setCurrent(notif, Display.getDisplay(mama).getCurrent());
        removeCommand(send);
        boolean failed = false;
        while(numbers.hasMoreElements())
        {
            String num = (String) numbers.nextElement();
            try
            {
                pushMessage(num, ave.getMessage() + " Y:" + ave.getNetwork());
            }
            catch(Exception e)
            {
                Alert sht = new Alert(num + " Failed", e.getMessage(), null, AlertType.ERROR);
                Display.getDisplay(mama).setCurrent(sht, this);
                failed = true;
            }
        }
        if(failed)
        {
            String prv;
            try
            {
                prv = StoreManager.read("messages");
            }
            catch(RecordStoreException rse)
            {
                prv = "";
            }
            if(prv.length() > 0)
            {
                prv = prv + "\0";
            }
            try
            {
                StoreManager.write("messages", prv + ave.getMessage() + " N:" + ave.getNetwork());
            }
            catch(RecordStoreException rse)
            {
                Alert sht = new Alert("Failed Messages", "Failed to store failed message: " + rse.getMessage(), null, AlertType.ERROR);
                Display.getDisplay(mama).setCurrent(sht, this);
            }
        }
        else
        {
            Alert gd = new Alert("Sent", "Messages sent. Will attempt to send any pending messages ...", null, AlertType.CONFIRMATION);
            Display.getDisplay(mama).setCurrent(gd, initial);
            try
            {
                String allem = StoreManager.read("messages");
                int combien = 0, pos = 0, notI = 0;
                themessages:
                while(true)
                {
                    pos     = allem.indexOf("\0", notI);
                    numbers = them.elements();
                    thenumbers:
                    while(numbers.hasMoreElements())
                    {
                        ++combien;
                        String num = (String) numbers.nextElement();
                        if(pos < 0)
                        {
                            pushMessage(num, allem.substring(notI));
                            break themessages;
                        }
                        else
                        {
                            pushMessage(num, allem.substring(notI, pos));
                        }
                        notI = pos + 1;
                    }
                }
                StoreManager.write("messages", "");
                if(combien > 0)
                {
                    Alert meh = new Alert("Sent", "Sent " + Integer.toString(combien) + " pending messages.", null, AlertType.CONFIRMATION);
                    Display.getDisplay(mama).setCurrent(meh, initial);
                }
            }
            catch(RecordStoreException rse) {}
            catch(IOException ioe) {}
        }
    }

    public void fetchInfo()
    {
        commandAction(send, this);
    }

    public void setAI(AvailabilityInformation ai)
    {
        ave = ai;
        this.setString(ai.getMessage());
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == beg)
        {
            Display.getDisplay(mama).setCurrent(initial);
            return;
        }
        sdr = new Thread(this);
        sdr.start();
    }
}

class DownloadImages extends List implements CommandListener, CarriesAvailabilityInformation, Runnable
{
    private Displayable prev, initial;
    private MIDlet mama;
    private Command dl, nx, st, prv;
    private SendSMS sdr;
    private AvailabilityInformation ave;
    private long largeLag, smallLag;
    private Thread dler;

    public DownloadImages(MIDlet m, Displayable p, Displayable i)
    {
        super("Downloading Images", Choice.EXCLUSIVE);
        mama    = m;
        prev    = p;
        initial = i;
        dl      = new Command("Download Images", Command.OK, 0);
        nx      = new Command("Send Messages", Command.OK, 0);
        st      = new Command("Stop!", Command.OK, 0);
        prv     = new Command("Back", Command.BACK, 1);
        addCommand(dl);
        addCommand(prv);
        setCommandListener(this);
    }

    public void run()
    {
        long largeStart = new Date().getTime();
        try
        {
            HttpConnection cn = (HttpConnection) Connector.open("http://dl.dropbox.com/u/17806012/img/test946.jpg");
            if(cn.getResponseCode() != 200)
            {
                Alert sht = new Alert("Download Failure", "Large image may have been cached!", null, AlertType.ERROR);
                Display.getDisplay(mama).setCurrent(sht, this);
            }
            InputStream ins = cn.openInputStream();
            byte [] dem     = new byte[ins.available()];
            ins.read(dem);
            ins.close();
            cn.close();
            largeLag = new Date().getTime() - largeStart;
        }
        catch(Exception ioe)
        {
            Alert sht = new Alert("Download Error (Large Image)", ioe.getMessage(), null, AlertType.ERROR);
            Display.getDisplay(mama).setCurrent(sht, this);
        }
        ave.setBigDLTime(largeLag);
        deleteAll();
        append("Large Image: " + Long.toString(largeLag) + " milliseconds", null);
        append("Downloading small image ...", null);
        long smallStart = new Date().getTime();
        try
        {
            HttpConnection cn = (HttpConnection) Connector.open("http://dl.dropbox.com/u/17806012/img/test432kb.jpg");
            if(cn.getResponseCode() != 200)
            {
                Alert sht = new Alert("Download Failure", "Small image may have been cached!", null, AlertType.ERROR);
                Display.getDisplay(mama).setCurrent(sht, this);
            }
            InputStream ins = cn.openInputStream();
            byte [] dem     = new byte[ins.available()];
            ins.read(dem);
            ins.close();
            cn.close();
            smallLag = new Date().getTime() - smallStart;
        }
        catch(Exception ioe)
        {
            Alert sht = new Alert("Download Error (small image)", ioe.getMessage(), null, AlertType.ERROR);
            Display.getDisplay(mama).setCurrent(sht, this);
        }
        ave.setSmallDLTime(smallLag);
        deleteAll();
        append("Large Image: " + Long.toString(largeLag) + " milliseconds", null);
        append("Small Image: " + Long.toString(smallLag) + " milliseconds", null);
        addCommand(nx);
        removeCommand(st);
    }

    public void fetchInfo()
    {
        commandAction(dl, this);
    }

    public void setAI(AvailabilityInformation ai)
    {
        ave = ai;
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == prv)
        {
            Display.getDisplay(mama).setCurrent(prev);
            return;
        }
        if(st == c)
        {
            dler.interrupt();
            Alert alt = new Alert("Stopped", "Not downloading anymore.", null, AlertType.CONFIRMATION);
            Display.getDisplay(mama).setCurrent(alt, this);
            deleteAll();
            append("Large Image: " + Long.toString(largeLag) + " milliseconds", null);
            append("Small Image: " + Long.toString(smallLag) + " milliseconds", null);
            addCommand(nx);
            return;
        }
        if(c == dl)
        {
            removeCommand(dl);
            addCommand(st);
            append("Downloading large image ...", null);
            dler = new Thread(this);
            try
            {
                dler.start();
            }
            catch(Exception e)
            {
                Alert alt = new Alert("Internet Error", e.getMessage(), null, AlertType.ERROR);
                Display.getDisplay(mama).setCurrent(alt, this);

            }
            return;
        }
        if(nx == c)
        {
            sdr = new SendSMS(mama, this, initial);
            ave.setBigDLTime(largeLag);
            ave.setSmallDLTime(smallLag);
            sdr.setAI(ave);
            Display.getDisplay(mama).setCurrent(sdr);
            return;
        }
    }
}

class FetchGPS extends Form implements CommandListener, CarriesAvailabilityInformation, Runnable
{
    private Command fetch, next, prev, man;
    private MIDlet mama;
    private Displayable initial, lst;
    private TextField loxn, loyn, signal;
    private ChoiceGroup sigav;
    private AvailabilityInformation ave;
    private StringItem junk;
    private Thread gpser;
    private boolean manual;

    public FetchGPS(MIDlet m, Displayable fst)
    {
        super("Signal and GPS");
        mama            = m;
        initial         = fst;
        fetch           = new Command("Getafix", Command.OK, 0);
        next            = new Command("Downloads", Command.OK, 0);
        prev            = new Command("Back", Command.OK, 1);
        man             = new Command("External GPS", Command.OK, 0);
        loyn            = new TextField("GPS Longitude", "", 10, TextField.ANY);
        loxn            = new TextField("GPS Latitude", "", 10, TextField.ANY);
        String ans      = System.getProperty("com.nokia.mid.networksignal"),
               juv      = System.getProperty("com.nokia.mid.networkavailability");
        String [] chcs  = {"Available", "Unavailable"};
        sigav           = new ChoiceGroup("Signal Available?", Choice.EXCLUSIVE, chcs, null);
        if((juv != null && juv.startsWith("available")) || ans != null)
            sigav.setSelectedIndex(0, true);
        else
            sigav.setSelectedIndex(1, true);
        signal          = new TextField("Signal Strength", (ans == null ? "0" : ans), 100, TextField.ANY);
        junk            = new StringItem("Be patient ...", "Looking for a fix, like any other junkie ...");
        manual          = false;
        addCommand(fetch);
        addCommand(prev);
        append(new StringItem("Signal Information", ""));
        append(sigav);
        append(signal);
        append(new StringItem("GPS Information", ""));
        append(junk);
        setCommandListener(this);
    }

    public void fetchInfo()
    {
        commandAction(fetch, this);
    }

    public void setAI(AvailabilityInformation ai)
    {
        ave = ai;
    }

    public void run()
    {
        commandAction(man, this);
        /*try
        {
            Criteria cr = new Criteria();
            cr.setHorizontalAccuracy(500);
            LocationProvider lp = LocationProvider.getInstance(cr);
            Location l = lp.getLocation(60);
            QualifiedCoordinates cds = l.getQualifiedCoordinates();
            if(! manual)
            {
                loxn.setString(new Double(cds.getLatitude()).toString());
                loyn.setString(new Double(cds.getLongitude()).toString());
                commandAction(man, this);
            }
        }
        catch(LocationException lex)
        {
            Alert fl = new Alert("Location Error", lex.getMessage(), null, AlertType.ERROR);
            Display.getDisplay(mama).setCurrent(fl, this);
            commandAction(man, this);
        }
        catch (InterruptedException iex)
        {
            commandAction(man, this);
        }
        catch(Exception uncool)
        {
            Alert fl = new Alert("No GPS", "No GPS. Going manual.", null, AlertType.ERROR);
            Display.getDisplay(mama).setCurrent(fl, this);
            commandAction(man, this);
        }
         * */
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == fetch)
        {
            removeCommand(fetch);
            addCommand(man);
            gpser = new Thread(this);
            try
            {
                gpser.start();
            }
            catch(Exception e)
            {
                commandAction(man, d);
            }
            return;
        }
        if(c == next)
        {
            DownloadImages dli = new DownloadImages(mama, this, initial);
            ave.setSignalAvailability(sigav.isSelected(0));
            ave.setSignalStrength(signal.getString());
            ave.setCoords(loxn.getString(), loyn.getString());
            dli.setAI(ave);
            Display.getDisplay(mama).setCurrent(dli);
            dli.fetchInfo();
            return;
        }
        if(c == man)
        {
            manual = true;
            gpser.interrupt();
            delete(4);
            removeCommand(man);
            append(loxn);
            append(loyn);
            addCommand(next);
            return;
        }
        Display.getDisplay(mama).setCurrent(initial);
    }
}

class FetchPlaceName extends Form implements CommandListener
{
    private MIDlet mama;
    private Command next, exit;
    private AvailabilityInformation ave;
    private TextField place, nums, netw;
    
    public FetchPlaceName(MIDlet m)
    {
        super("Basic Details");
        String prev, nw;
        try
        {
            prev = StoreManager.read("numbers");
        }
        catch(RecordStoreException rse)
        {
            prev = "";
        }
        try
        {
            nw = StoreManager.read("network");
        }
        catch(RecordStoreException rse)
        {
            nw = "";
        }
        place   = new TextField("Name of the place?", "", 30, TextField.ANY);
        nums    = new TextField("Dot-separated numbers to send to?", prev, 500, TextField.ANY);
        netw    = new TextField("Current Network's Name?", nw, 10, TextField.ANY);
        mama    = m;
        exit    = new Command("Exit", Command.EXIT, 1);
        next    = new Command("Load GPS", Command.OK, 0);
        addCommand(exit);
        addCommand(next);
        append(place);
        append(nums);
        append(netw);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == exit)
        {
            mama.notifyDestroyed();
            return;
        }
        Vector numbits  = new Vector();
        String thg      = nums.getString();
        try
        {
            StoreManager.write("numbers", thg);
        }
        catch(RecordStoreException rse) {}
        try
        {
            StoreManager.write("network", netw.getString());
        }
        catch(RecordStoreException rse) {}
        if(thg.trim().equals(""))
            thg = "256772344681";
        for(int notI = 0, pos = 0; ; pos = notI + 1)
        {
            if(pos > thg.length())
                break;
            notI = thg.indexOf(".", pos);
            if(notI < 0)
            {
                numbits.addElement(thg.substring(pos));
                break;
            }
            numbits.addElement(thg.substring(pos, notI));
        }
        ave           = new AvailabilityInformation(place.getString(), numbits);
        ave.setNetwork(netw.getString());
        FetchGPS fgps = new FetchGPS(mama, this);
        fgps.setAI(ave);
        Display.getDisplay(mama).setCurrent(fgps);
        try
        {
            fgps.fetchInfo();
        }
        catch(Exception e)
        {
            Alert nogps = new Alert("No GPS", "Get a cooler phone.", null, AlertType.ERROR);
            Display.getDisplay(mama).setCurrent(nogps, fgps);
        }
    }
}

public class Availability extends MIDlet
{
    public void startApp()
    {
        FetchPlaceName fps = new FetchPlaceName(this);
        Display.getDisplay(this).setCurrent(fps);
    }

    public void pauseApp()
    {

    }

    public void destroyApp(boolean unconditional)
    {

    }
}
