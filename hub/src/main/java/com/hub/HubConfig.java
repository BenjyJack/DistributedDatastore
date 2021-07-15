package com.hub;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.net.InetAddress;

@Configuration
@EnableScheduling
public class HubConfig {
    private final HubController hubController;
    private Long currentLeader;
    HubConfig(HubController hubController){
        this.hubController = hubController;
    }
    @Scheduled(fixedDelay = 30000)
    protected void randomlyCheck() throws IOException {
        this.currentLeader = Long.parseLong(this.hubController.basicGetLeader());
        if(this.currentLeader != null){
            InetAddress address = InetAddress.getByName(this.hubController.getLocalMap().get(currentLeader));
            if(!address.isReachable(300)){
                this.hubController.findLeader();
                this.hubController.sendLeader();
            }
        }
    }
}
