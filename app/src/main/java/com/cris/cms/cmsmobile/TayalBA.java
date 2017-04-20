package com.cris.cms.cmsmobile;

/**
 * Created by odi on 29/07/16.
 */
public class TayalBA {




public static void turnOn(UsbService usbService)
{
    byte[] d = new byte[] { (byte)0x80,  (byte)0xAA,};
    usbService.write(d);

}


    public static void turnOff(UsbService usbService)
    {
        byte[] d = new byte[] { (byte)0x80,  (byte)0xAA,};
        usbService.write(d);

    }




    public static void left(UsbService usbService)
    {
        byte[] d = new byte[] { (byte)0x81,  (byte)0x01,};
        usbService.write(d);

    }



    public static void right(UsbService usbService)
    {
        byte[] d = new byte[] { (byte)0x81,  (byte)0x02,};
        usbService.write(d);

    }


    public static void disable(UsbService usbService)
    {
        byte[] d = new byte[] { (byte)0x81,  (byte)0x5A,};
        usbService.write(d);

    }

    public static void enable(UsbService usbService)
    {
        byte[] d = new byte[] { (byte)0x81,  (byte)0x02,};
        usbService.write(d);

    }


}
