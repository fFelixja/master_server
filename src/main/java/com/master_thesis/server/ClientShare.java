package com.master_thesis.server;

public class ClientShare {

    private int share;
    private int clientID;
    private int transformatorID;
    private int proofComponent;


    public int getProofComponent() {
        return proofComponent;
    }

    public void setProofComponent(int proofComponent) {
        this.proofComponent = proofComponent;
    }

    public int getShare() {
        return share;
    }

    public void setShare(int share) {
        this.share = share;
    }

    public int getClientID() {
        return clientID;
    }

    public void setClientID(int clientID) {
        this.clientID = clientID;
    }

    public int getTransformatorID() {
        return transformatorID;
    }

    public void setTransformatorID(int transformatorID) {
        this.transformatorID = transformatorID;
    }
}
