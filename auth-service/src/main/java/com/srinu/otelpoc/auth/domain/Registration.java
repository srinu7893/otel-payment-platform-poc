package com.srinu.otelpoc.auth.domain;
import jakarta.persistence.*;
@Entity @Table(name="registration")
public class Registration {
    @Id @Column(length=80) private String username;
    @Column(nullable=false,unique=true,length=64) private String accountNumber;
    @Column(nullable=false,length=120) private String displayName;
    @Column(nullable=false,unique=true,length=160) private String email;
    @Column(nullable=false,length=20) private String state="PENDING";
    protected Registration(){}
    public Registration(String username,String number,String name,String email){this.username=username;accountNumber=number;displayName=name;this.email=email;}
    public String getUsername(){return username;}
    public String getAccountNumber(){return accountNumber;}
    public String getDisplayName(){return displayName;}
    public String getEmail(){return email;}
    public String getState(){return state;}
    public void complete(){state="ACTIVE";}
}
