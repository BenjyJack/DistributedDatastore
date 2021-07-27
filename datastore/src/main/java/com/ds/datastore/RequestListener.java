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
        String requestID = request.getHeader("requestID");
        if(requestID == null){
            requestID = String.valueOf(UUID.randomUUID());
        }
        request.setAttribute("requestID", requestID);
        logger.info("Call from {}, Request Type: {}, Request {} received", request.getRequestURI(), request.getMethod(), requestID);
    }
}
