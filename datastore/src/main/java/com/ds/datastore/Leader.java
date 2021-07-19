package com.ds.datastore;

import org.springframework.stereotype.Component;

@Component
public class Leader {
    private Long leader;

    public Long getLeader() {
        return leader;
    }

    public void setLeader(Long leader) {
        this.leader = leader;
    }

}
