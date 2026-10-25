package com.campus.lostfound.model;

public class Claim {
    private final String id, itemId, claimantId, proof, status, createdAt;
    public Claim(String id, String itemId, String claimantId, String proof, String status, String createdAt) {
        this.id=id; this.itemId=itemId; this.claimantId=claimantId; this.proof=proof; this.status=status; this.createdAt=createdAt;
    }
    public String getId(){return id;}
    public String getItemId(){return itemId;}
    public String getClaimantId(){return claimantId;}
    public String getProof(){return proof;}
    public String getStatus(){return status;}
    public String getCreatedAt(){return createdAt;}
}
