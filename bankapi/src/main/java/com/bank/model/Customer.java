package com.bank.model;

public class Customer
{
    private int id;
    private String username;
    private String fullName;

    public Customer()
    {

    }

    public Customer(int id, String username, String fullName)
    {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
    }

    //----------------------------------------------------------------GETTERS----------------------------------------------------------------

    public int getId()
    {
        return id;
    }

    public String getUsername()
    {
        return username;
    }

    public String getFullName()
    {
        return fullName;
    }

    //----------------------------------------------------------------SETTERS----------------------------------------------------------------

    public void setFullName(String fullName)
    {
        this.fullName = fullName;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    //no setter for id, hard coded at seed
}