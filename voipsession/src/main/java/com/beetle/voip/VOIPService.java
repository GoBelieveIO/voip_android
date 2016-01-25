package com.beetle.voip;

import com.beetle.im.IMService;


/**
 * Created by houxh on 14-7-21.
 */
public class VOIPService extends IMService {

    private static VOIPService im = new VOIPService();

    public static VOIPService getInstance() {
        return im;
    }

}
