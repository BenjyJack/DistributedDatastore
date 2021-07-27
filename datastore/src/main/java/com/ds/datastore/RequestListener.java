package com.ds.datastore;

import java.util.UUID;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
public class RequestListener implements ServletRequestListener{
    private Logger logger = LoggerFactory.getLogger(RequestListener.class);
    @Override
    public void requestInitialized(ServletRequestEvent sre){
        HttpServletRequest request = (HttpServletRequest)sre.getServletRequest();
        String orderID = request.getHeader("orderID");
        if(orderID == null){
            orderID = String.valueOf(UUID.randomUUID());
            request.setAttribute("orderID", orderID);
        }
        logger.info("Call from {}", request.getHeader("referer"));
        logger.info("Request Type: {}", request.getMethod());
        logger.info("Request {} received", orderID);
    }
}
