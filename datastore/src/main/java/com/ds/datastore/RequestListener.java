package com.ds.datastore;

import java.util.UUID;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
public class RequestListener implements ServletRequestListener{
    private Logger logger = LoggerFactory.getLogger(RequestListener.class);
    @Override
    public void requestInitialized(ServletRequestEvent sre){
        ServletRequest request = sre.getServletRequest(); 
        if(request.getAttribute("orderID") == null){
            request.setAttribute("orderID", String.valueOf(UUID.randomUUID()));
        }
        logger.info("Request {} received", request.getAttribute("orderID"));
    }
}
